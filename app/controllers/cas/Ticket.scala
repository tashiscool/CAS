package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait Ticket {
  /**
   * Method to retrieve the id.
   *
   * @return the id
   */
  def getId: String

  /**
   * Determines if the ticket is expired. Most common implementations might
   * collaborate with <i>ExpirationPolicy </i> strategy.
   *
   * @return true, if the ticket is expired
   * @see org.jasig.cas.ticket.ExpirationPolicy
   */
  def isExpired: Boolean

  /**
   * Method to retrieve the TicketGrantingTicket that granted this ticket.
   *
   * @return the ticket or null if it has no parent
   */
  def getGrantingTicket: Option[TicketGrantingTicket]

  /**
   * Method to return the time the Ticket was created.
   *
   * @return the time the ticket was created.
   */
  def getCreationTime: Long

  /**
   * @return the number of times this ticket was used.
   */
  def getCountOfUses: Int
}

trait TicketState{
  /**
   * Returns the number of times a ticket was used.
   *
   * @return the number of times the ticket was used.
   */
  def getCountOfUses: Int

  /**
   * Returns the last time the ticket was used.
   *
   * @return the last time the ticket was used.
   */
  def getLastTimeUsed: Long

  /**
   * Get the second to last time used.
   *
   * @return the previous time used.
   */
  def getPreviousTimeUsed: Long

  /**
   * Get the time the ticket was created.
   *
   * @return the creation time of the ticket.
   */
  def getCreationTime: Long

  /**
   * Authentication information from the ticket. This may be null.
   *
   * @return the authentication information.
   */
  def getAuthentication: Authentication
}

trait BaseTicket extends Ticket with TicketState {
  def expirationPolicy:ExpirationPolicy
  def id:String
  def ticketGrantingTicket: Option[TicketGrantingTicket]
  def lastTimeUsed: Long
  def previousLastTimeUsed:Long
  def creationTime:Long
  def countOfUses:Int

}
object BaseTicket