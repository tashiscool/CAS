package controllers.cas

object TicketGrantingTicket{
  /** The prefix to use when generating an id for a Ticket Granting Ticket. */
  val PREFIX: String = "TGT"
  /** The prefix to use when generating an id for a Proxy Granting Ticket. */
  val PROXY_GRANTING_TICKET_PREFIX: String = "PGT"
  /** The prefix to use when generating an id for a Proxy Granting Ticket IOU. */
  val PROXY_GRANTING_TICKET_IOU_PREFIX: String = "PGTIOU"
}

/**
 * Created by tash on 11/19/15.
 */
trait TicketGrantingTicket extends BaseTicket {

  /**
   * Method to retrieve the authentication.
   *
   * @return the authentication
   */
  def getAuthentication: Authentication

  /**
   * Gets a list of supplemental authentications associated with this ticket.
   * A supplemental authentication is one other than the one used to create the ticket,
   * for example, a forced authentication that happens after the beginning of a CAS SSO session.
   *
   * @return Non-null list of supplemental authentications.
   */
  def getSupplementalAuthentications: List[Authentication]

  /**
   * Grant a ServiceTicket for a specific service.
   *
   * @param id The unique identifier for this ticket.
   * @param service The service for which we are granting a ticket
   * @param expirationPolicy the expiration policy.
   * @param credentialsProvided if the credentials are provided.
   * @return the service ticket granted to a specific service for the
   *         principal of the TicketGrantingTicket
   */
  def grantServiceTicket(id: String, service: Service, expirationPolicy: ExpirationPolicy, credentialsProvided: Boolean): (ServiceTicket, TicketGrantingTicket)

  /**
   * Gets an immutable map of service ticket and services accessed by this ticket-granting ticket.
   *
   * @return an immutable map of service ticket and services accessed by this ticket-granting ticket.
   */
  def getServices: Map[String, Service]

  /**
   * Remove all services of the TGT (at logout).
   */
  def removeAllServices:TicketGrantingTicket

  /**
   * Mark a ticket as expired.
   */
  def markTicketExpired:TicketGrantingTicket

  /**
   * Convenience method to determine if the TicketGrantingTicket is the root
   * of the hierarchy of tickets.
   *
   * @return true if it has no parent, false otherwise.
   */
  def isRoot: Boolean

  /**
   * Gets the ticket-granting ticket at the root of the ticket hierarchy.
   *
   * @return Non-null root ticket-granting ticket.
   */
  def getRoot: TicketGrantingTicket

  /**
   * Gets all authentications ({@link #getAuthentication()}, {@link #getSupplementalAuthentications()}) from this
   * instance and all dependent tickets that reference this one.
   *
   * @return Non-null list of authentication associated with this ticket in leaf-first order.
   */
  def getChainedAuthentications: List[Authentication]

  /**
   * Gets the service that produced a proxy-granting ticket.
   *
   * @return  Service that produced proxy-granting ticket or null if this is
   *          not a proxy-granting ticket.
   * @since 4.1
   */
  def getProxiedBy: Service
}

case class TicketGrantingTicketImpl(override val expirationPolicy: ExpirationPolicy,
                                    override val  id:String,
                                    override val  ticketGrantingTicket: Option[TicketGrantingTicket],
                                    override val  lastTimeUsed: Long = 0,
                                    override val  previousLastTimeUsed:Long = 0,
                                    override val  creationTime:Long = 0 ,
                                    override val  countOfUses:Int = 0,
                                     authentication: Authentication, expired: Boolean = false, proxiedBy:Option[Service],
                                    services: Map[String, Service] = Map.empty,supplementalAuthentications: List[Authentication] = List.empty)
  extends TicketGrantingTicket {
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
   * Method to retrieve the authentication.
   *
   * @return the authentication
   */
  override def getAuthentication: Authentication = authentication

  /**
   * Gets an immutable map of service ticket and services accessed by this ticket-granting ticket.
   *
   * @return an immutable map of service ticket and services accessed by this ticket-granting ticket.
   */
  override def getServices: Map[String, Service] = services

  /**
   * Remove all services of the TGT (at logout).
   */
  override def removeAllServices: TicketGrantingTicket = this.copy(services = Map())

  /**
   * Gets a list of supplemental authentications associated with this ticket.
   * A supplemental authentication is one other than the one used to create the ticket,
   * for example, a forced authentication that happens after the beginning of a CAS SSO session.
   *
   * @return Non-null list of supplemental authentications.
   */
  override def getSupplementalAuthentications: List[Authentication] = supplementalAuthentications

  /**
   * Gets all authentications ({@link #getAuthentication()}, {@link #getSupplementalAuthentications()}) from this
   * instance and all dependent tickets that reference this one.
   *
   * @return Non-null list of authentication associated with this ticket in leaf-first order.
   */
  override def getChainedAuthentications: List[Authentication] = getGrantingTicket.get.getChainedAuthentications

  /**
   * Mark a ticket as expired.
   */
  override def markTicketExpired: TicketGrantingTicket = this.copy(expired = false)

  /**
   * Gets the ticket-granting ticket at the root of the ticket hierarchy.
   *
   * @return Non-null root ticket-granting ticket.
   */
  override def getRoot: TicketGrantingTicket = ticketGrantingTicket.getOrElse(this)

  /**
   * Grant a ServiceTicket for a specific service.
   *
   * @param id The unique identifier for this ticket.
   * @param service The service for which we are granting a ticket
   * @param expirationPolicy the expiration policy.
   * @param credentialsProvided if the credentials are provided.
   * @return the service ticket granted to a specific service for the
   *         principal of the TicketGrantingTicket
   */
  override def grantServiceTicket(id: String, service: Service, expirationPolicy: ExpirationPolicy, credentialsProvided: Boolean): (ServiceTicket, TicketGrantingTicket) = {
    val logn = (this.getCountOfUses == 0 || credentialsProvided)
    val serviceTicket: ServiceTicket = new ServiceTicketImpl(id = id, ticketGrantingTicket = Some(this), service = service, fromNewLogin = logn, expirationPolicy = expirationPolicy)

    val authentications: List[Authentication] = getChainedAuthentications
    service.setPrincipal(authentications(authentications.size - 1).getPrincipal)

    val ticket = updateState.copy(services = services+(id-> service))

    (serviceTicket, ticket)
  }

  /**
   * Convenience method to determine if the TicketGrantingTicket is the root
   * of the hierarchy of tickets.
   *
   * @return true if it has no parent, false otherwise.
   */
  override def isRoot: Boolean = ticketGrantingTicket == this

  /**
   * Gets the service that produced a proxy-granting ticket.
   *
   * @return  Service that produced proxy-granting ticket or null if this is
   *          not a proxy-granting ticket.
   * @since 4.1
   */
  override def getProxiedBy: Service = proxiedBy.get

  def updateState: TicketGrantingTicketImpl = {
    this.copy(previousLastTimeUsed = lastTimeUsed, lastTimeUsed = System.currentTimeMillis, countOfUses = countOfUses+1)
  }

  /**
   * Returns the last time the ticket was used.
   *
   * @return the last time the ticket was used.
   */
  override def getLastTimeUsed: Long = lastTimeUsed

  /**
   * Get the second to last time used.
   *
   * @return the previous time used.
   */
  override def getPreviousTimeUsed: Long = previousLastTimeUsed
}