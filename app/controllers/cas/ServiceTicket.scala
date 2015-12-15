package controllers.cas


object ServiceTicket {
  /** Prefix generally applied to unique ids generated
    * by org.jasig.cas.util.UniqueTicketIdGenerator.
    * */
  val PREFIX: String = "ST"
  /** Proxy ticket prefix applied to unique ids
    * generated by org.jasig.cas.util.UniqueTicketIdGenerator.
    * */
  val PROXY_TICKET_PREFIX: String = "PT"
}

/**
 * Created by tash on 11/19/15.
 */
trait ServiceTicket extends BaseTicket{

  /**
   * Retrieve the service this ticket was given for.
   *
   * @return the server.
   */
  def getService: Service

  /**
   * Determine if this ticket was created at the same time as a
   * TicketGrantingTicket.
   *
   * @return true if it is, false otherwise.
   */
  def isFromNewLogin: Boolean

  /**
   * Attempts to ensure that the service specified matches the service associated with the ticket.
   * @param service The incoming service to match this service ticket against.
   * @return true, if the match is successful.
   */
  def isValidFor(service: Service): Boolean

  /**
   * Method to grant a TicketGrantingTicket from this service to the
   * authentication. Analogous to the ProxyGrantingTicket.
   *
   * @param id The unique identifier for this ticket.
   * @param authentication The Authentication we wish to grant a ticket for.
   * @param expirationPolicy expiration policy associated with this ticket
   * @return The ticket granting ticket.
   */
  def grantTicketGrantingTicket(id: String, authentication: Authentication, expirationPolicy: ExpirationPolicy): (TicketGrantingTicket, ServiceTicket)
}
case class ServiceTicketImpl(override val expirationPolicy: ExpirationPolicy,
                             override val  id:String,
                             override val  ticketGrantingTicket: Option[TicketGrantingTicket],
                             override val  lastTimeUsed: Long = 0,
                             override val  previousLastTimeUsed:Long = 0,
                             override val  creationTime:Long = 0, service: Service, grantedTicketAlready :Boolean = false,
                             override val  countOfUses:Int = 0, expired: Boolean = false, fromNewLogin: Boolean) extends ServiceTicket {
  /**
   * Method to retrieve the id.
   *
   * @return the id
   */
  override def getId: String = id

  /**
   * Method to retrieve the TicketGrantingTicket that granted this ticket.
   *
   * @return the ticket or null if it has no parent
   */
  override def getGrantingTicket: Option[TicketGrantingTicket] = ticketGrantingTicket

  /**
   * Determines if the ticket is expired. Most common implementations might
   * collaborate with <i>ExpirationPolicy </i> strategy.
   *
   * @return true, if the ticket is expired
   * @see org.jasig.cas.ticket.ExpirationPolicy
   */
  override def isExpired: Boolean = expired

  /**
   * @return the number of times this ticket was used.
   */
  override def getCountOfUses: Int = countOfUses

  /**
   * Method to return the time the Ticket was created.
   *
   * @return the time the ticket was created.
   */
  override def getCreationTime: Long = creationTime

  /**
   * Retrieve the service this ticket was given for.
   *
   * @return the server.
   */
  override def getService: Service = service

  /**
   * Method to grant a TicketGrantingTicket from this service to the
   * authentication. Analogous to the ProxyGrantingTicket.
   *
   * @param id The unique identifier for this ticket.
   * @param authentication The Authentication we wish to grant a ticket for.
   * @param expirationPolicy expiration policy associated with this ticket
   * @return The ticket granting ticket.
   */
  override def grantTicketGrantingTicket(id: String, authentication: Authentication, expirationPolicy: ExpirationPolicy): (TicketGrantingTicket, ServiceTicket) = {
    this synchronized {
      if (this.grantedTicketAlready) {
        throw new IllegalStateException("TicketGrantingTicket already generated for this ServiceTicket.  Cannot grant more than one TGT for ServiceTicket")
      }
    }
    (TicketGrantingTicketImpl(id = id, proxiedBy = Some(service), ticketGrantingTicket = this.getGrantingTicket,
      authentication =  authentication, expirationPolicy =expirationPolicy), this.copy(grantedTicketAlready = true))
  }

  /**
   * Attempts to ensure that the service specified matches the service associated with the ticket.
   * @param service The incoming service to match this service ticket against.
   * @return true, if the match is successful.
   */
  override def isValidFor(service: Service): Boolean = this.service.matches(service)

  /**
   * Determine if this ticket was created at the same time as a
   * TicketGrantingTicket.
   *
   * @return true if it is, false otherwise.
   */
  override def isFromNewLogin: Boolean = fromNewLogin

  /**
   * Returns the last time the ticket was used.
   *
   * @return the last time the ticket was used.
   */
  override def getLastTimeUsed: Long = lastTimeUsed

  /**
   * Authentication information from the ticket. This may be null.
   *
   * @return the authentication information.
   */
  override def getAuthentication: Authentication = getGrantingTicket.get.getAuthentication

  /**
   * Get the second to last time used.
   *
   * @return the previous time used.
   */
  override def getPreviousTimeUsed: Long = previousLastTimeUsed
}