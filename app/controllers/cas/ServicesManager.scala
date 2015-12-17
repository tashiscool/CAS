package controllers.cas

import java.net.URL

import akka.util.ReentrantGuard
import com.google.inject.Inject
import org.slf4j.LoggerFactory
import play.Logger
import play.api.Play
import play.api.libs.concurrent.Akka

import scala.concurrent.{duration, Future}
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * Created by tash on 11/23/15.
 */
trait ServicesManager {
  /**
   * Register a service with CAS, or update an existing an entry.
   *
   * @param registeredService the RegisteredService to update or add.
   * @return newly persisted RegisteredService instance
   */
  def save(registeredService: RegisteredService): Future[RegisteredService]

  /**
   * Delete the entry for this RegisteredService.
   *
   * @param id the id of the registeredService to delete.
   * @return the registered service that was deleted, null if there was none.
   */
  def delete(id: Long): Future[RegisteredService]

  /**
   * Find a RegisteredService by matching with the supplied service.
   *
   * @param service the service to match with.
   * @return the RegisteredService that matches the supplied service.
   */
  def findServiceBy(service: Service): Future[RegisteredService]

  /**
   * Find a RegisteredService by matching with the supplied id.
   *
   * @param id the id to match with.
   * @return the RegisteredService that matches the supplied service.
   */
  def findServiceBy(id: Long): Future[RegisteredService]

  /**
   * Retrieve the collection of all registered services.
   *
   * @return the collection of all services.
   */
  def getAllServices: Future[Seq[RegisteredService]]

  /**
   * Convenience method to let one know if a service exists in the data store.
   *
   * @param service the service to check.
   * @return true if it exists, false otherwise.
   */
  def matchesExistingService(service: Service): Future[Boolean]
}

case class DefaultServicesManagerImpl @Inject() (serviceRegistryDao: ServiceRegistryDao, defaultAttributes: List[String] ) extends ServicesManager {
  import Play.current
  private val LOGGER = LoggerFactory.getLogger(classOf[DefaultServicesManagerImpl] )

  //todo: redo this with while(wait);
  val cacheLock = new ReentrantGuard

  val one = 1
  val five = 5

  Akka.system.scheduler.schedule(FiniteDuration(1,duration.SECONDS), FiniteDuration(5,duration.SECONDS)) {
    Logger.trace("Clearing chat status cache")
    cacheLock.withGuard {
      Try(this.load) match {
        case Success(_) if(services.isEmpty) =>
          val registeredService = RegisteredServiceImpl(serviceId = "http://google.com", name="1", theme="sso", id = 1, description = "dumb protected content", evaluationOrder = 0,
            logo = new URL("http://google.com"), logoutUrl = new URL("http://google.com"),publicKey=null)
          services.put(1,registeredService)
        case Failure(e) if(services.isEmpty) => Logger.error(s"Error clearing chat status cache", e)
          val registeredService = RegisteredServiceImpl(serviceId = "1", name="1", theme="sso", id = 1, description = "dumb protected content", evaluationOrder = 0,
            logo = new URL("http://google.com"), logoutUrl = new URL("http://google.com"),publicKey=null)
          services.put(1,registeredService)
      }
    }
  }

  /** Map to store all services. */
  private val services = collection.mutable.Map.empty[Long, RegisteredService]

  def delete (id: Long): Future[RegisteredService] = {
    val r: Future[RegisteredService] = findServiceBy(id)
    r.flatMap{ s =>
      this.serviceRegistryDao.delete(s).map{_ => cacheLock.withGuard(this.services.remove(id)); s}
    }
  }

  /**
   * {@inheritDoc}
   */
  def findServiceBy(service: Service): Future[RegisteredService] = {
    Future.successful(cacheLock.withGuard(services.values.find(x => x.matches(service)) ).getOrElse(null))
  }

  def findServiceBy(id: Long): Future[RegisteredService] = {
    Future.successful(cacheLock.withGuard(services.find{case (x,y) => x == id}.map(_._2)).getOrElse(null))
  }


  def matchesExistingService (service: Service): Future[Boolean] = {
    return findServiceBy(service).map{ _ != null}
  }

  def save (registeredService: RegisteredService): Future[RegisteredService] = {
    this.serviceRegistryDao.save(registeredService).map{ x =>
      cacheLock.withGuard(this.services.put(x.getId, x))
      x
    }
  }

  def reload {
    LOGGER.info ("Reloading registered services.")
    load
  }

  /**
   * Load services that are provided by the DAO.
   */
  private def load {
    val localServices: collection.mutable.Map[Long, RegisteredService] = collection.mutable.Map.empty[Long, RegisteredService]

    import scala.collection.JavaConversions._
    this.serviceRegistryDao.load.map{ s =>
      val serviceMap = s.map{x => (x.getId, x)}.toMap[Long,RegisteredService]
      LOGGER.debug (s"Adding registered service ${serviceMap}")
      this.services.putAll(serviceMap)
    }
    LOGGER.info ("Loaded {} services.", this.services.size)
  }

  /**
   * Retrieve the collection of all registered services.
   *
   * @return the collection of all services.
   */
  override def getAllServices: Future[Seq[RegisteredService]] = Future.successful(cacheLock.withGuard(this.services.values.toSeq))
}



