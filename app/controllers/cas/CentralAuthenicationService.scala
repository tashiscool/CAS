package controllers.cas

import java.security.PublicKey
import javax.sql.RowSet

import com.google.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.slf4j.{LoggerFactory, Logger}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Created by tash on 11/19/15.
 */
trait CentralAuthenicationService {
  /**
   * Create a {@link org.jasig.cas.ticket.TicketGrantingTicket} by authenticating credentials.
   * The details of the security policy around credential authentication and the definition
   * of authentication success are dependent on the implementation, but it SHOULD be safe to assume
   * that at least one credential MUST be authenticated for ticket creation to succeed.
   *
   * @param credentials One or more credentials that may be authenticated in order to create the ticket.
   *
   * @return Non-null ticket-granting ticket identifier.
   *
   */
  def createTicketGrantingTicket(credentials: Seq[Credentials]): Future[TicketGrantingTicket]


  /**
   * Obtains the given ticket by its id and type
   * and returns the CAS-representative object. Implementations
   * need to check for the validity of the ticket by making sure
   * it exists and has not expired yet, etc. This method is specifically
   * designed to remove the need to access the ticket registry.
   *
   * @param ticketId the ticket granting ticket id
   * @param clazz the ticket type that is reques to be found
   * @return the ticket object
   * @since 4.1.0
   */
  def getTicket[T <: Ticket ](ticketId: String, clazz: Class[T]): Future[Option[T]]

  /**
   * Retrieve a collection of tickets from the underlying ticket registry.
   * The retrieval operation must pass the predicate check that is solely
   * used to filter the collection of tickets received. Implementations
   * can use the predicate to request a collection of expired tickets,
   * or tickets whose id matches a certain pattern, etc. The resulting
   * collection will include ticktes that have been evaluated by the predicate.
   *
   * @return the tickets
   * @since 4.1.0
   */
  def getTickets(evaluate: RowSet => Boolean) :Ticket
  def getTickets(evaluate: (AnyRef, Int) => Boolean) :Ticket
  def getTickets(evaluate: (AnyRef, String, Int) => Boolean) :Ticket

  /**
   * Grants a {@link org.jasig.cas.ticket.ServiceTicket} that may be used to access the given service.
   *
   * @param ticketGrantingTicketId Proof of prior authentication.
   * @param service The target service of the ServiceTicket.
   *
   * @return Non-null service ticket identifier.
   *
   */
  def grantServiceTicket( ticketGrantingTicketId: String,  service: Service): Future[ServiceTicket]

  /**
   * Grant a {@link org.jasig.cas.ticket.ServiceTicket} that may be used to access the given service
   * by authenticating the given credentials.
   * The details of the security policy around credential authentication and the definition
   * of authentication success are dependent on the implementation, but it SHOULD be safe to assume
   * that at least one credential MUST be authenticated for ticket creation to succeed.
   * <p>
   * The principal that is resolved from the authenticated credentials MUST be the same as that to which
   * the given ticket-granting ticket was issued.
   * </p>
   *
   * @param ticketGrantingTicketId Proof of prior authentication.
   * @param service The target service of the ServiceTicket.
   * @param credentials One or more credentials to authenticate prior to granting the service ticket.
   *
   * @return Non-null service ticket identifier.
   *
   */
  def grantServiceTicket( ticketGrantingTicketId: String,  service: Service, credentials: Seq[Credentials]): Future[ServiceTicket]

  /**
   * Validate a ServiceTicket for a particular Service.
   *
   * @param serviceTicketId Proof of prior authentication.
   * @param service Service wishing to validate a prior authentication.
   *
   * @return Non-null ticket validation assertion.
   *
   */
  def validateServiceTicket(serviceTicketId: String, service: Service): Future[Assertion]

  /**
   * Destroy a TicketGrantingTicket and perform back channel logout. This has the effect of invalidating any
   * Ticket that was derived from the TicketGrantingTicket being destroyed. May throw an
   * {@link IllegalArgumentException} if the TicketGrantingTicket ID is null.
   *
   * @param ticketGrantingTicketId the id of the ticket we want to destroy
   * @return the logout requests.
   */
  def destroyTicketGrantingTicket(ticketGrantingTicketId: String): Future[List[LogoutRequest]]

  /**
   * Delegate a TicketGrantingTicket to a Service for proxying authentication
   * to other Services.
   *
   * @param serviceTicketId The service ticket identifier that will delegate to a
   * @param credentials One or more credentials to authenticate prior to delegating the ticket.
   *0
   * @return Non-null ticket-granting ticket identifier that can grant
   * that proxy authentication.
   *
   */
  def delegateTicketGrantingTicket(serviceTicketId:String, credentials: Seq[Credentials]): Future[TicketGrantingTicket]
}

class CentralAuthenticationServiceImpl @Inject() (val ticketGrantingTicketExpirationPolicy: ExpirationPolicy, val serviceTicketExpirationPolicy: ExpirationPolicy,
                                            val ticketRegistry: TicketRegistry, val logOutManager: LogoutManager, val authenticationManager: AuthenticationManager,
                                            val servicesManager: ServicesManager,val ticketGrantingTicketUniqueTicketIdGenerator: UniqueTicketIdGenerator
                                            ,val principalFactory: PrincipalFactory) extends CentralAuthenicationService {
  protected final val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val serviceContextAuthenticationPolicyFactory = new AcceptAnyAuthenticationPolicyFactory()

  override def createTicketGrantingTicket(credentials: Seq[Credentials]): Future[TicketGrantingTicket] = {
    val sanitizedCredentials: Set[Credentials] = credentials.filterNot{case x => (x == null) }.toSet
    if (!sanitizedCredentials.isEmpty) {
      val idForTicket = ticketGrantingTicketUniqueTicketIdGenerator.getNewTicketId(TicketGrantingTicketImpl.getClass.getName)
      val authentication: Authentication = this.authenticationManager.authenticate(credentials)
      val ticketGrantingTicket = TicketGrantingTicketImpl(id = idForTicket, proxiedBy = None, ticketGrantingTicket = None,
        authentication =  authentication, expirationPolicy =ticketGrantingTicketExpirationPolicy)
      this.ticketRegistry.addTicket(ticketGrantingTicket).map{
        _ => ticketGrantingTicket
      }
    }else{
      val msg: String = "No credentials were specified in the request for creating a new ticket-granting ticket"
      //logger.warn(msg)
      throw new RuntimeException(new IllegalArgumentException(msg))
    }
  }

  override def getTickets(evaluate: (RowSet) => Boolean): Ticket = ???

  override def getTickets(evaluate: (AnyRef, Int) => Boolean): Ticket = ???

  override def getTickets(evaluate: (AnyRef, String, Int) => Boolean): Ticket = ???

  override def getTicket[T <: Ticket](ticketId: String, clazz: Class[T]): Future[Option[T]] = {
    if(StringUtils.isBlank(ticketId))
      throw new RuntimeException("ticketId cannot be blank")
    val ticketOptionFuture: Future[Option[Ticket]] = this.ticketRegistry.getTicket(ticketId, clazz)
    ticketOptionFuture.map{ case ticketOption =>
      ticketOption match { case Some(ticket) =>
        if (ticket == null) {
          logger.debug(s"Ticket [${ticketId}] by type [${clazz.getSimpleName}] cannot be found in the ticket registry." )
          None
        }
        ticket match {
          case ticketInstance: BaseTicket if (ticketInstance.isExpired) =>
            this.ticketRegistry.deleteTicket(ticketId)
            logger.debug(s"Ticket [${ticketId}] has expired and is now deleted from the ticket registry.")
            None
          case _ => Some(ticket.asInstanceOf[T])
        }
      case _ => None
      }
    }
  }

  override def validateServiceTicket(serviceTicketId: String, service: Service): Future[Assertion] = {
    val registeredServiceFuture: Future[RegisteredService] = this.servicesManager.findServiceBy(service)
    registeredServiceFuture.flatMap{registeredService =>
      verifyRegisteredServiceProperties(registeredService, service)
      val serviceTicketOptionFuture: Future[Option[ServiceTicket]] = this.ticketRegistry.getTicket(serviceTicketId, classOf[ServiceTicket])
      serviceTicketOptionFuture.map { case Some(serviceTicketOption) =>
        val serviceTicket = serviceTicketOption
        try {
          serviceTicket synchronized {
            if (serviceTicket.isExpired) {
              logger.info(s"ServiceTicket [${serviceTicketId}] has expired.")
              throw new InvalidTicketException(serviceTicketId)
            }
            if (!serviceTicket.isValidFor(service)) {
              logger.error(s"Service ticket [${serviceTicketId}] with service [${serviceTicket.getService.getId}] does not match supplied service [${service}]")
              throw new UnrecognizableServiceForServiceTicketValidationException(serviceTicket.getService)
            }
          }
          val root: TicketGrantingTicket = serviceTicket.getGrantingTicket.get.getRoot
          val authentication: Authentication = getAuthenticationSatisfiedByPolicy(root, new ServiceContext(serviceTicket.getService, registeredService))
          val principal: Principal = authentication.getPrincipal
          val attributePolicy: AttributeReleasePolicy = registeredService.getAttributeReleasePolicy
          logger.debug(s"Attribute policy [${attributePolicy}] is associated with service [${registeredService}]")
          @SuppressWarnings(Array("unchecked")) val attributesToRelease: Map[String, AnyRef] = if (attributePolicy != null) attributePolicy.getAttributes(principal) else Map.empty[String, AnyRef]
          val principalId: String = registeredService.getUsernameAttributeProvider.resolveUsername(principal, service)
          val modifiedPrincipal: Principal = this.principalFactory.createPrincipal(principalId, attributesToRelease)
          val builder: AuthenticationBuilder = DefaultAuthenticationBuilder.newInstance(authentication)
          builder.setPrincipal(modifiedPrincipal)
          new ImmutableAssertion(builder.build, serviceTicket.getGrantingTicket.get.getChainedAuthentications, serviceTicket.getService, serviceTicket.isFromNewLogin)
        } finally {
          if (serviceTicket.isExpired) {
            this.ticketRegistry.deleteTicket(serviceTicketId)
          }
        }
      case _ =>  logger.info(s"Service ticket [${serviceTicketId}] does not exist.")
        throw new InvalidTicketException(serviceTicketId)
      }
    }
  }

  override def destroyTicketGrantingTicket(ticketGrantingTicketId: String): Future[List[LogoutRequest]] = {
    try {
      logger.debug(s"Removing ticket [{ticketGrantingTicketId}] from registry...")
      val ticketFuture: Future[TicketGrantingTicket] = getTicket(ticketGrantingTicketId, classOf[TicketGrantingTicket]).map(_.getOrElse(null))
      logger.debug("Ticket found. Processing logout requests and then deleting the ticket...")
      ticketFuture.map { ticket =>
        val logoutRequests: List[LogoutRequest] = this.logOutManager.performLogout(ticket)
        this.ticketRegistry.deleteTicket(ticketGrantingTicketId)
        logoutRequests
      }
    }
    catch {
      case e: InvalidTicketException => {
        logger.debug(s"TicketGrantingTicket [${ticketGrantingTicketId}] cannot be found in the ticket registry.")
        Future.successful(List.empty[LogoutRequest])
      }
    }
  }

  override def grantServiceTicket(ticketGrantingTicketId: String, service: Service): Future[ServiceTicket] = this.grantServiceTicket(ticketGrantingTicketId, service, List.empty[Credentials].toSeq)

  override def grantServiceTicket(ticketGrantingTicketId: String, service: Service, credentials: Seq[Credentials]): Future[ServiceTicket] = {
    val ticketGrantingTicketfuture: Future[TicketGrantingTicket] = getTicket(ticketGrantingTicketId, classOf[TicketGrantingTicket]).map(_.getOrElse(null))
    ticketGrantingTicketfuture.flatMap{ticketGrantingTicket =>
      val registeredServiceFuture: Future[RegisteredService] = this.servicesManager.findServiceBy(service)
      registeredServiceFuture.map{ registeredService=>

        verifyRegisteredServiceProperties(registeredService, service)
        val sanitizedCredentials: Set[Credentials] = credentials.filterNot{case x => (x == null) }.toSet

        var currentAuthentication: Authentication = null
        if (sanitizedCredentials.size > 0) {
          currentAuthentication = this.authenticationManager.authenticate(sanitizedCredentials.toSeq)
          val original: Authentication = ticketGrantingTicket.getAuthentication
          if (!(currentAuthentication.getPrincipal == original.getPrincipal)) {
            throw new MixedPrincipalException(currentAuthentication, currentAuthentication.getPrincipal, original.getPrincipal)
          }
          val newTicket = ticketGrantingTicket match {
            case tgt: TicketGrantingTicketImpl => tgt.copy(supplementalAuthentications = tgt.getSupplementalAuthentications.::(currentAuthentication))
          }
          //todo: do something with new ticket
        }

        if (currentAuthentication == null && !registeredService.getAccessStrategy.isServiceAccessAllowedForSso) {
          logger.warn(s"ServiceManagement: Service [${service.getId}] is not allowed to use SSO.")
          throw new UnauthorizedSsoServiceException
        }

        val proxiedBy: Service = ticketGrantingTicket.getProxiedBy
        if (proxiedBy != null) {
          logger.debug(s"TGT is proxied by [${proxiedBy.getId}]. Locating proxy service in registry...")
          val proxyingServiceFuture: Future[RegisteredService] = servicesManager.findServiceBy(proxiedBy)
          proxyingServiceFuture.map{proxyingService=>
            if (proxyingService != null) {
              logger.debug(s"Located proxying service [${proxyingService}] in the service registry")
              if (!proxyingService.getProxyPolicy.isAllowedToProxy) {
                logger.warn(s"Found proxying service ${proxyingService.getId}, but it is not authorized to fulfill the proxy attempt made by ${service.getId}")
                throw new UnauthorizedProxyingException("Proxying is not allowed for registered service " + registeredService.getId)
              }
            }
            else {
              logger.warn(s"No proxying service found. Proxy attempt by service [${service.getId}] (registered service [${registeredService.getId}]) is not allowed.")
              throw new UnauthorizedProxyingException("Proxying is not allowed for registered service " + registeredService.getId)
            }

          }
        }
        else {
          logger.trace("TGT is not proxied by another service")
        }

        // Perform security policy check by getting the authentication that satisfies the configured policy
        // This throws if no suitable policy is found
        getAuthenticationSatisfiedByPolicy(ticketGrantingTicket, new ServiceContext(service, registeredService))

        val authentications: List[Authentication] = ticketGrantingTicket.getChainedAuthentications
        val principal: Principal = authentications(authentications.size - 1).getPrincipal

        val principalAttrs: Map[String, AnyRef] = registeredService.getAttributeReleasePolicy.getAttributes(principal)
        if (!registeredService.getAccessStrategy.doPrincipalAttributesAllowServiceAccess(principalAttrs)) {
          logger.warn(s"ServiceManagement: Cannot grant service ticket because Service [${service.getId}] is not authorized for use by [${principal}].")
          throw new UnauthorizedServiceForPrincipalException
        }

        val uniqueTicketIdGenKey: String = service.getClass.getName
        logger.debug(s"Looking up service ticket id generator for [${uniqueTicketIdGenKey}]")


        val ticketPrefix: String = if (authentications.size == 1) ServiceTicket.PREFIX else ServiceTicket.PROXY_TICKET_PREFIX
        val ticketId: String = DefaultUniqueTicketIdGenerator(DefaultLongNumericGenerator(0), DefaultRandomStringGenerator(35), "").getNewTicketId(ticketPrefix)
        val serviceTicket: ServiceTicket = ticketGrantingTicket.grantServiceTicket(ticketId, service, this.serviceTicketExpirationPolicy, currentAuthentication != null)._1

        this.ticketRegistry.addTicket(serviceTicket)

        logger.info(s"Granted ticket [${serviceTicket.getId}] for service [${service.getId}] for user [${principal.getId}]")
        serviceTicket
      }
    }
  }

  override def delegateTicketGrantingTicket(serviceTicketId: String, credentials: Seq[Credentials] ): Future[TicketGrantingTicket] = {
    val serviceTicketFuture:Future[Option[ServiceTicket]] = this.ticketRegistry.getTicket(serviceTicketId, classOf[ServiceTicket])
    serviceTicketFuture.flatMap { case Some(serviceTicket) =>
      if (serviceTicket == null || serviceTicket.isExpired) {
        logger.debug(s"ServiceTicket [${serviceTicketId}] has expired or cannot be found in the ticket registry")
        throw new InvalidTicketException(serviceTicketId)
      }

      val registeredServiceFuture: Future[RegisteredService] = this.servicesManager.findServiceBy(serviceTicket.getService)
      registeredServiceFuture.map{
        registeredService =>
          verifyRegisteredServiceProperties(registeredService, serviceTicket.getService)

          if (!registeredService.getProxyPolicy.isAllowedToProxy) {
            logger.warn(s"ServiceManagement: Service [${serviceTicket.getService.getId}] attempted to proxy, but is not allowed.")
            throw new UnauthorizedProxyingException(s"ServiceManagement: Service ${serviceTicket.getService.getId} attempted to proxy, but is not allowed.")
          }

          val authentication: Authentication = this.authenticationManager.authenticate(credentials)

          val pgtId: String = this.ticketGrantingTicketUniqueTicketIdGenerator.getNewTicketId(TicketGrantingTicket.PROXY_GRANTING_TICKET_PREFIX)
          val proxyGrantingTicket: TicketGrantingTicket = serviceTicket.grantTicketGrantingTicket(pgtId, authentication, this.ticketGrantingTicketExpirationPolicy)._1

          logger.debug(s"Generated proxy granting ticket [${proxyGrantingTicket}] based off of [${serviceTicketId}]")
          proxyGrantingTicket match {
            case proxyGTicket:Ticket =>this.ticketRegistry.addTicket(proxyGTicket)
          }
          proxyGrantingTicket
      }

    case _ => logger.debug(s"ServiceTicket [${serviceTicketId}] has expired or cannot be found in the ticket registry")
      throw new InvalidTicketException(serviceTicketId)
    }

  }

  private def getAuthenticationSatisfiedByPolicy(ticket: TicketGrantingTicket, context: ServiceContext) :Authentication = {
    val policy: ContextualAuthenticationPolicy[ServiceContext] = serviceContextAuthenticationPolicyFactory.createPolicy(context)
    if (policy.isSatisfiedBy(ticket.getAuthentication)) {
      return ticket.getAuthentication
    }
    import scala.collection.JavaConversions._
    for (auth <- ticket.getSupplementalAuthentications) {
      if (policy.isSatisfiedBy(auth)) {
        return auth
      }
    }
    throw new UnsatisfiedAuthenticationPolicyException(policy)
  }

  /**
   * Ensure that the service is found and enabled in the service registry.
   * @param registeredService the located entry in the registry
   * @param service authenticating service
   */
  private def verifyRegisteredServiceProperties(registeredService: RegisteredService, service: Service) {
    if (registeredService == null) {
      val msg: String = String.format("ServiceManagement: Unauthorized Service Access. " + "Service [%s] is not found in service registry.", service.getId)
      logger.warn(msg)
      throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, msg)
    }
    if (!registeredService.getAccessStrategy.isServiceAccessAllowed) {
      val msg: String = String.format("ServiceManagement: Unauthorized Service Access. " + "Service [%s] is not enabled in service registry.", service.getId)
      logger.warn(msg)
      throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, msg)
    }
  }
}

case class ServiceContext(service: Service, registeredService: RegisteredService)

class UnauthorizedServiceForPrincipalException extends RuntimeException

class UnsatisfiedAuthenticationPolicyException(policy: ContextualAuthenticationPolicy[ServiceContext]) extends RuntimeException

class UnauthorizedProxyingException(s: String) extends RuntimeException

class UnauthorizedSsoServiceException extends RuntimeException

class MixedPrincipalException(currentAuthentication: Authentication, getPrincipal: Principal, getPrincipal1: Principal) extends RuntimeException

class UnauthorizedServiceException(CODE_UNAUTHZ_SERVICE: String, msg: String) extends RuntimeException

object UnauthorizedServiceException {
  val CODE_UNAUTHZ_SERVICE = "screen.service.error.message";

  /** Exception object that indicates the service manager is empty with no service definitions. **/
  val CODE_EMPTY_SVC_MGMR = "screen.service.empty.error.message";
}

class InvalidTicketException(serviceTicketId: String) extends RuntimeException

class UnrecognizableServiceForServiceTicketValidationException(getService: Service) extends RuntimeException

/**
 * Defines operations to create principals.
 * @author Misagh Moayyed
 * @since 4.1.0
 */
trait PrincipalFactory {
  /**
   * Create principal.
   *
   * @param id the id
   * @return the principal
   */
  def createPrincipal(id: String): Principal

  /**
   * Create principal along with its attributes.
   *
   * @param id the id
   * @param attributes the attributes
   * @return the principal
   */
  def createPrincipal(id: String, attributes: Map[String, AnyRef]): Principal
}


class DefaultPrincipalFactory extends PrincipalFactory {

  def createPrincipal(id: String): Principal = {
    return new SimplePrincipal(id, Map())
  }

  def createPrincipal(id: String, attributes: Map[String, AnyRef] ): Principal = {
    return new SimplePrincipal(id, attributes)
  }
}