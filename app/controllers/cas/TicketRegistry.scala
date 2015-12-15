package controllers.cas

/**
 * Created by tash on 11/23/15.
 */
trait TicketRegistry {
  /**
   * Add a ticket to the registry. Ticket storage is based on the ticket id.
   *
   * @param ticket The ticket we wish to add to the cache.
   */
  def addTicket(ticket: Ticket)

  /**
   * Retrieve a ticket from the registry. If the ticket retrieved does not
   * match the expected class, an InvalidTicketException is thrown.
   *
   * @param ticketId the id of the ticket we wish to retrieve.
   * @param clazz The expected class of the ticket we wish to retrieve.
   * @return the requested ticket.
   */
  def getTicket[T <: Ticket](ticketId: String, clazz: Class[_ <: Ticket]): Option[T]

  /**
   * Retrieve a ticket from the registry.
   *
   * @param ticketId the id of the ticket we wish to retrieve
   * @return the requested ticket.
   */
  def getTicket(ticketId: String): Ticket

  /**
   * Remove a specific ticket from the registry.
   * If ticket to delete is TGT then related service tickets are removed as well.
   *
   * @param ticketId The id of the ticket to delete.
   * @return true if the ticket was removed and false if the ticket did not
   *         exist.
   */
  def deleteTicket(ticketId: String): Boolean

  /**
   * Retrieve all tickets from the registry.
   *
   * @return collection of tickets currently stored in the registry. Tickets
   *         might or might not be valid i.e. expired.
   */
  def getTickets: Seq[Ticket]
}
