package utils.scalautils

import java.io._

import controllers.cas._

import scala.util.{Failure, Success}
import net.spy.memcached.internal._
import net.spy.memcached.{CachedData, AddrUtil, ConnectionFactoryBuilder, MemcachedClient}
import net.spy.memcached.auth.AuthDescriptor
import net.spy.memcached.auth.PlainCallbackHandler
import net.spy.memcached.compat.log.AbstractLogger
import net.spy.memcached.compat.log.Level
import net.spy.memcached.transcoders.Transcoder
import play.api.{Application, Logger}

import scala.collection.JavaConverters._
import scala.concurrent.{ Future, Promise }
import scala.language.implicitConversions
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Created by tash on 12/15/15.
 */
trait CacheService {
  def set[A](key: String, value: A, expiration: Int = 60): Future[Boolean]
  def get[A](key: String): Future[Option[A]]
  def remove(key: String): Future[Boolean]
}

class Slf4JLogger(name: String) extends AbstractLogger(name) {

  val logger = Logger("memcached")

  def isDebugEnabled = logger.isDebugEnabled

  def isInfoEnabled = logger.isInfoEnabled

  def log(level: Level, msg: AnyRef, throwable: Throwable) {
    val message = msg.toString
    level match {
      case Level.DEBUG => logger.debug(message, throwable)
      case Level.INFO => logger.info(message, throwable)
      case Level.WARN => logger.warn(message, throwable)
      case Level.ERROR => logger.error(message, throwable)
      case Level.FATAL => logger.error("[FATAL] " + message, throwable)
      case _ => logger.trace(message, throwable)
    }
  }

  override def isTraceEnabled: Boolean = logger.isTraceEnabled
}

class CacheServiceImpl extends CacheService {

  import play.api.Play.current

  import CacheOps._

  lazy val namespace: String = implicitly[Application].configuration.getString("memcached.namespace").getOrElse("")

  lazy val client = {
    val app: Application = implicitly[Application]
    System.setProperty("net.spy.log.LoggerImpl", "services.sapi.Slf4JLogger")

    val username = Option(System.getenv("MEMCACHIER_USERNAME"))
    val password = Option(System.getenv("MEMCACHIER_PASSWORD"))
    val endpointOption = Option(System.getenv("MEMCACHIER_SERVERS"))

    app.configuration.getString("elasticache.config.endpoint").map { endpoint =>
      new MemcachedClient(AddrUtil.getAddresses(endpoint))
    }.getOrElse {
      lazy val singleHost = endpointOption.map(AddrUtil.getAddresses)
      lazy val multipleHosts = endpointOption.map(AddrUtil.getAddresses).map { _ =>
        def accumulate(nb: Int): String = {
          app.configuration.getString("memcached." + nb + ".host").map { h => h + " " + accumulate(nb + 1) }.getOrElse("")
        }
        AddrUtil.getAddresses(accumulate(1))
      }

      val addrs = singleHost.orElse(multipleHosts)
        .getOrElse(throw new RuntimeException("Bad configuration for memcached: missing host(s)"))

      username.map { memcacheUser =>
        val memcachePassword = password.getOrElse {
          throw new RuntimeException("Bad configuration for memcached: missing password")
        }

        // Use plain SASL to connect to memcached
        val ad = new AuthDescriptor(
          Array("PLAIN"),
          new PlainCallbackHandler(memcacheUser, memcachePassword)
        )
        val cf = new ConnectionFactoryBuilder()
          .setProtocol(ConnectionFactoryBuilder.Protocol.BINARY)
          .setAuthDescriptor(ad)
          .build()

        new MemcachedClient(cf, addrs)
      }.getOrElse {
        new MemcachedClient(addrs)
      }
    }
  }

  override def set[A](key: String, value: A, expiration: Int = 3600): Future[Boolean] = {
    Logger.debug(s"Writing object to cache with key $namespace$key")
    val f = client.set(namespace + key, expiration, value, new UniversalTranscoder[A]).map {
      case Some(returned) => returned.booleanValue()
      case None => false
    } recover {
      case t: Throwable =>
        Logger.error(s"Error writing cache object with key $key: ", t)
        false
    }

    f.onComplete {
      case Success(b) if b =>
        Logger.debug(s"Cache set($key) success")
      case Success(b) if !b =>
        Logger.error(s"Cache set($key) failed")
      case Failure(e) =>
        Logger.error(s"Cache set($key) failed", e)
    }

    f
  }

  override def get[A](key: String): Future[Option[A]] = {
    Logger.debug(s"Fetching object from cache with key $namespace$key")
    client.asyncGet(namespace + key, new UniversalTranscoder[A])
  }

  override def remove(key: String): Future[Boolean] = {
    Logger.debug(s"Removing object from cache with key $namespace$key")
    val f = client.delete(key).map {
      case Some(returned) => returned.booleanValue()
      case None => false
    } recover {
      case t: Throwable =>
        Logger.error(s"Error removing cache object with key $key: ", t)
        false
    }

    f.onComplete {
      case Success(b) if b =>
        Logger.debug(s"Cache set($key) success")
      case Success(b) if !b =>
        Logger.error(s"Cache set($key) failed")
      case Failure(e) =>
        Logger.error(s"Cache set($key) failed", e)
    }

    f
  }
}

class NoopCacheServiceImpl extends CacheService {
  override def set[A](key: String, value: A, expiration: Int): Future[Boolean] = Future.successful(true)

  override def get[A](key: String): Future[Option[A]] = Future.successful(None)

  override def remove(key: String): Future[Boolean] = Future.successful(true)
}

object CacheOps {

  class UniversalTranscoder[T] extends Transcoder[T] {
    //Not sure about this one.... DSH
    override def asyncDecode(cachedData: CachedData): Boolean = false

    override def encode(t: T): CachedData = {
      val bos: ByteArrayOutputStream = new ByteArrayOutputStream()
      new ObjectOutputStream(bos).writeObject(t)
      new CachedData(0, bos.toByteArray, CachedData.MAX_SIZE)
    }

    override def getMaxSize: Int = CachedData.MAX_SIZE

    override def decode(cachedData: CachedData): T = {
      new ObjectInputStream(new ByteArrayInputStream(cachedData.getData)) {
        override protected def resolveClass(desc: ObjectStreamClass) = {
          Class.forName(desc.getName(), false, play.api.Play.current.classloader)
        }
      }.readObject().asInstanceOf[T]
    }
  }

  implicit def operationFutureAsScala[T](underlying: OperationFuture[T]): Future[Option[T]] = {
    val p = Promise[Option[T]]()
    underlying.addListener(new OperationCompletionListener {
      def onComplete(f: OperationFuture[_]) {
        val status = f.getStatus //f is underlying
        if (status.isSuccess)
          p success Option(underlying.get)
        else if (status.getMessage == "NOT_FOUND")
          p success None
        else
          p failure new RuntimeException(status.getMessage)

      }
    })
    p.future
  }

  implicit def getFutureAsScala[T](underlying: GetFuture[T]): Future[Option[T]] = {
    val p = Promise[Option[T]]()
    underlying.addListener(new GetCompletionListener {
      def onComplete(f: GetFuture[_]) {
        val status = f.getStatus //f is underlying
        if (status.isSuccess)
          p success Option(underlying.get)
        else if (status.getMessage == "NOT_FOUND")
          p success None
        else
          p failure new RuntimeException(status.getMessage)
      }
    })
    p.future
  }

  implicit def bulkGetFutureAsScala[T](underlying: BulkGetFuture[T]): Future[Map[String, T]] = {
    val p = Promise[Map[String, T]]()
    underlying.addListener(new BulkGetCompletionListener {
      def onComplete(f: BulkGetFuture[_]) {
        val status = f.getStatus //f is underlying
        if (status.isSuccess)
          p success underlying.get.asScala.toMap //java.util.Map -> mutable.Map -> immutable.Map
        else
          p failure new Exception(status.getMessage)
      }
    })
    p.future
  }

  def futureCaching[A](sessionId: String)(expr: => Future[A])(implicit keyGenerator: KeyGenerator[A], cacheService: CacheService): Future[A] = expr.flatMap { something =>
    cacheService.set(keyGenerator.apply(sessionId, something), something).map {
      _ => something
    }
  }

  def caching[A](sessionId: String)(expr: => A)(implicit keyGenerator: KeyGenerator[A], cacheService: CacheService): Future[A] = {
    val result = expr
    cacheService.set(keyGenerator.apply(sessionId, result), result).map {
      _ => result
    }
  }
}

trait KeyGenerator[A] {
  def apply(sessionId: String, value: A): String
}

object Keys{
  implicit val serviceGenerator: KeyGenerator[Service] = new KeyGenerator[Service] {
    override def apply(sessionId: String, value: Service): String = s"/$sessionId/service/${NullSafe(value.getId).getOrElse("")}"
  }
  implicit val registeredServiceGenerator: KeyGenerator[RegisteredService] = new KeyGenerator[RegisteredService] {
    override def apply(sessionId: String, value: RegisteredService): String = s"/$sessionId/registeredService/${NullSafe(value.getId.toString).getOrElse("")}"
  }
  implicit val ticketGrantingTicketGenerator: KeyGenerator[TicketGrantingTicketImpl] = new KeyGenerator[TicketGrantingTicketImpl] {
    override def apply(sessionId: String, value: TicketGrantingTicketImpl): String = s"/$sessionId/ticketGrantingTicketImpl/${NullSafe(value.getId).getOrElse("")}"
  }
  implicit val serviceTicketGenerator: KeyGenerator[ServiceTicketImpl] = new KeyGenerator[ServiceTicketImpl] {
    override def apply(sessionId: String, value: ServiceTicketImpl): String = s"/$sessionId/serviceTicketImpl/${NullSafe(value.getId).getOrElse("")}"
  }
  implicit val ticketGenerator: KeyGenerator[Ticket] = new KeyGenerator[Ticket] {
    override def apply(sessionId: String, value: Ticket): String = s"/$sessionId/Ticket/${NullSafe(value.getId).getOrElse("")}"
  }
  implicit val uspwCredentialsGenerator: KeyGenerator[UsernamePasswordCredential] = new KeyGenerator[UsernamePasswordCredential] {
    override def apply(sessionId: String, value: UsernamePasswordCredential): String = s"/$sessionId/usernamePassword/${value.id}"
  }
  implicit val credentialsGenerator: KeyGenerator[Credentials] = new KeyGenerator[Credentials] {
    override def apply(sessionId: String, value: Credentials): String = s"/$sessionId/credentials/${value.id}"
  }
  implicit val serviceTickGenerator: KeyGenerator[ServiceTicket] = new KeyGenerator[ServiceTicket] {
    override def apply(sessionId: String, value: ServiceTicket): String = s"/$sessionId/serviceTicket/${NullSafe(value.getId).getOrElse("")}"
  }
}