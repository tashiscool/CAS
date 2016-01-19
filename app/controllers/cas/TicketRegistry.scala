package controllers.cas

import com.google.inject.Inject
import org.slf4j.LoggerFactory
import utils.scalautils.{KeyGenerator, Keys, CacheOps, CacheService}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import utils.scalautils.Keys._
import controllers.cas.BaseTicket

/**
 * Created by tash on 11/23/15.
 */
trait TicketRegistry extends Serializable{
  /**
   * Add a ticket to the registry. Ticket storage is based on the ticket id.
   *
   * @param ticket The ticket we wish to add to the cache.
   */
  def addTicket[T <: Ticket](ticket: T)(implicit keyGenerator: KeyGenerator[T]):Future[Ticket]

  /**
   * Retrieve a ticket from the registry. If the ticket retrieved does not
   * match the expected class, an InvalidTicketException is thrown.
   *
   * @param ticketId the id of the ticket we wish to retrieve.
   * @param clazz The expected class of the ticket we wish to retrieve.
   * @return the requested ticket.
   */
  def getTicket[T <: Ticket](ticketId: String)(implicit keyGenerator: KeyGenerator[T]): Future[Option[T]]

  /**
   * Remove a specific ticket from the registry.
   * If ticket to delete is TGT then related service tickets are removed as well.
   *
   * @param ticketId The id of the ticket to delete.
   * @return true if the ticket was removed and false if the ticket did not
   *         exist.
   */
  def deleteTicket(ticketId: String): Future[Boolean]

  /**
   * Retrieve all tickets from the registry.
   *
   * @return collection of tickets currently stored in the registry. Tickets
   *         might or might not be valid i.e. expired.
   */
  def getTickets: Seq[Ticket]
}
class MemcacheTicketRegistry @Inject() (val cacheService: CacheService) extends TicketRegistry {
   implicit val c = cacheService

   val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Add a ticket to the registry. Ticket storage is based on the ticket id.
   *
   * @param ticket The ticket we wish to add to the cache.
   */
  override def addTicket[T <: Ticket](ticket: T)(implicit keyGenerator: KeyGenerator[T]):Future[Ticket] ={
    //We shouldn't need this
//    ticket match{
//      case tgt:TicketGrantingTicket=> val f = CacheOps.caching("")(tgt); f.onComplete(x => logger.debug(s"ticketgt added to memcache $ticket $x")); f
//      case st:ServiceTicket=> val f = CacheOps.caching("")(st); f.onComplete(_ => logger.debug(s"sticket added to memcache $ticket")); f
//      case st:BaseTicket=> val f = CacheOps.caching("")(st); f.onComplete(_ => logger.debug(s"bticket added to memcache $ticket")); f
//      case _ => val f = CacheOps.caching("")(ticket); f.onComplete(_ => logger.debug(s"ticket added to memcache $ticket")); f
//    }

    val f = CacheOps.caching("")(ticket)
    f.onComplete(_ => logger.debug(s"ticket added to memcache $ticket"))
    f
  }

  /**
   * Retrieve all tickets from the registry.
   *
   * @return collection of tickets currently stored in the registry. Tickets
   *         might or might not be valid i.e. expired.
   */
  override def getTickets: Seq[Ticket] = throw new UnsupportedOperationException("GetTickets not supported.")

  /**
   * Retrieve a ticket from the registry. If the ticket retrieved does not
   * match the expected class, an InvalidTicketException is thrown.
   *
   * @param ticketId the id of the ticket we wish to retrieve.
   * @param clazz The expected class of the ticket we wish to retrieve.
   * @return the requested ticket.
   */
  override def getTicket[T <: Ticket](ticketId: String)(implicit keyGenerator: KeyGenerator[T]): Future[Option[T]] = {

   import TicketTransformers._
   val ticket = new Ticket {
      override def getId: String = ticketId
      override def getGrantingTicket: Option[TicketGrantingTicket] = ???
      override def isExpired: Boolean = ???
      override def getCountOfUses: Int = ???
      override def getCreationTime: Long = ???
   }

    val serviceTicket : ServiceTicket = ticket
    val ticketGrantingTicket : TicketGrantingTicket = ticket

    if(ticketId.contains("TGT")){
      cacheService.get[T](Keys.ticketGrantingTicketGenerator.apply("", ticketGrantingTicket) )
    }else if (ticketId.contains("ST")){
      cacheService.get[T](Keys.baseTicketGenerator.apply("", serviceTicket) )
    }else{
      cacheService.get[T](Keys.ticketGenerator.apply("", ticket) )
    }

  }
  

  /**
   * Remove a specific ticket from the registry.
   * If ticket to delete is TGT then related service tickets are removed as well.
   *
   * @param ticketId The id of the ticket to delete.
   * @return true if the ticket was removed and false if the ticket did not
   *         exist.
   */
  override def deleteTicket(ticketId: String): Future[Boolean] = {
    val f = cacheService.remove(ticketId);
    f.onComplete(_ => logger.debug(s"removing $ticketId"));
    f
  }
}

object TicketTransformers {
  implicit def asServiceTicket(ticket: Ticket) : ServiceTicket = {
    new ServiceTicketImpl(expirationPolicy = null, id = ticket.getId, ticketGrantingTicket = None,service = null, fromNewLogin = false)
  }
  implicit def asTicketGrantingTicket(ticket: Ticket) : TicketGrantingTicket = {
    new TicketGrantingTicketImpl(expirationPolicy = null, id = ticket.getId, ticketGrantingTicket = None, authentication = null, proxiedBy = None)
  }
}