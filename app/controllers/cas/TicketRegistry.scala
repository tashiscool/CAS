package controllers.cas

import com.google.inject.Inject
import org.slf4j.LoggerFactory
import utils.scalautils.{Keys, CacheOps, CacheService}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import utils.scalautils.Keys._

/**
 * Created by tash on 11/23/15.
 */
trait TicketRegistry {
  /**
   * Add a ticket to the registry. Ticket storage is based on the ticket id.
   *
   * @param ticket The ticket we wish to add to the cache.
   */
  def addTicket(ticket: Ticket):Future[Ticket]

  /**
   * Retrieve a ticket from the registry. If the ticket retrieved does not
   * match the expected class, an InvalidTicketException is thrown.
   *
   * @param ticketId the id of the ticket we wish to retrieve.
   * @param clazz The expected class of the ticket we wish to retrieve.
   * @return the requested ticket.
   */
  def getTicket[T <: Ticket](ticketId: String, clazz: Class[_ <: Ticket]): Future[Option[T]]

  /**
   * Retrieve a ticket from the registry.
   *
   * @param ticketId the id of the ticket we wish to retrieve
   * @return the requested ticket.
   */
  def getTicket(ticketId: String): Future[Option[Ticket]]

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
  override def addTicket(ticket: Ticket): Future[Ticket] ={
    ticket match{
      case tgt:TicketGrantingTicketImpl=> val f = CacheOps.caching("")(tgt); f.onComplete(_ => logger.debug(s"ticket added to memcache $ticket")); f
      case st:ServiceTicketImpl=> val f = CacheOps.caching("")(st); f.onComplete(_ => logger.debug(s"ticket added to memcache $ticket")); f
      case _ => val f = CacheOps.caching("")(ticket); f.onComplete(_ => logger.debug(s"ticket added to memcache $ticket")); f
    }
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
  override def getTicket[T <: Ticket](ticketId: String, clazz: Class[_ <: Ticket]): Future[Option[T]] = {

   val ticket = new Ticket {
      override def getId: String = ticketId
      override def getGrantingTicket: Option[TicketGrantingTicket] = ???
      override def isExpired: Boolean = ???
      override def getCountOfUses: Int = ???
      override def getCreationTime: Long = ???
   }
    val serviceTicket = new ServiceTicketImpl(expirationPolicy = null, id = ticketId, ticketGrantingTicket = None,service = null, fromNewLogin = false)
    val ticketGrantingticket = new TicketGrantingTicketImpl(expirationPolicy = null, id = ticketId, ticketGrantingTicket = None, authentication = null, proxiedBy = None)


    clazz match{
      case _ if (clazz.getName == TicketGrantingTicketImpl.getClass.getName) => cacheService.get[T](Keys.ticketGrantingTicketGenerator.apply("", ticketGrantingticket) )
      case _ if (clazz.getName == ServiceTicketImpl.getClass.getName)=> cacheService.get[T](Keys.serviceTicketGenerator.apply("", serviceTicket) )
      case _ => cacheService.get[T](Keys.ticketGenerator.apply("", ticket) )
    }
  }


  /**
   * Retrieve a ticket from the registry.
   *
   * @param ticketId the id of the ticket we wish to retrieve
   * @return the requested ticket.
   */
  override def getTicket(ticketId: String): Future[Option[Ticket]] = cacheService.get[Ticket](ticketId)

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