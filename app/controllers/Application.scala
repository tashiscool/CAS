package controllers

import com.google.inject.Inject
import controllers.cas._
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import play.api.mvc._
import utils.scalautils.{Keys, CacheOps, CacheService}
import play.api.libs.concurrent.Execution.Implicits._
import utils.scalautils.Keys._

import scala.concurrent.Future


class Application @Inject()(val casService: CentralAuthenicationService, val servicesManager: ServicesManager, val cacheService: CacheService)  extends Controller {

  val logger = LoggerFactory.getLogger(this.getClass)
  implicit val c: CacheService = cacheService

  //TODO: we can probably use implicits here for some cleanup -tk
  def getTgtId(request: Request[AnyContent]):String = request.cookies.get("tgt").map(_.value).getOrElse("")
  def getServiceLocation(request: Request[AnyContent]) = request.getQueryString("service").getOrElse("")
  def getGatewayLocation(request: Request[AnyContent]) = request.getQueryString("gateway").getOrElse("")



  def TicketAction(gatewayRequestCheck: (Request[AnyContent]) => Future[Result], terminateSession: (Request[AnyContent]) => Future[Result],
                   hasServiceCheck: (Request[AnyContent], String) => Future[Result] ) = Action.async { implicit request =>
    initialFlowSetupAction(request)
    val tgtId = getTgtId(request)
    if(StringUtils.isBlank(tgtId)){
      gatewayRequestCheck(request)
    }else {
      val someTicket = casService.getTicket(tgtId, classOf[BaseTicket])
      someTicket match{
        case Some(ticket) if(!ticket.isExpired) => hasServiceCheck(request, getServiceLocation(request))
        case _ =>terminateSession(request)
      }
    }
  }

  def index = Action {Ok}

  def serviceAuthorizationCheck = Action {Ok}

  def renewRequestCheck = Action {Ok}

  def hasServiceCheck = Action {Ok}

  def gatewayServicesManagementCheck = Action.async{ implicit request =>
   WebUtils.getService(request, cacheService).map{ case Some(service) =>
     if(servicesManager.matchesExistingService(service)) Ok
     else BadRequest("doesn't match")
   case _ => BadRequest("no gateway/service found")
   }
  }

  def gatewayRequestCheck(gatewayServicesManagementCheck: (Request[AnyContent]) => Future[Result],
                          serviceAuthorizationCheck: (Request[AnyContent]) => Future[Result])=Action.async { implicit request =>
    if( StringUtils.isNotBlank(getServiceLocation(request)) && StringUtils.isNotBlank(getGatewayLocation(request)) )
      gatewayServicesManagementCheck(request)
    else
      serviceAuthorizationCheck(request)
  }

  def terminateSession(gatewayRequestCheck: (Request[AnyContent]) => Future[Result]) = Action.async { implicit request =>
    gatewayRequestCheck(request).map(_.withCookies(Cookie("tgt","")))
  }

  def initialFlowSetupAction(request: Request[AnyContent]): Future[Any] = {
    val context = request

    val service: Service = WebUtils.getArgumentsExtractors(context)


    val futureServices = if (service != null) {
      logger.debug("Placing service in context scope: [{}]", service.getId)
      val registeredService: RegisteredService = servicesManager.findServiceBy(service)
      if (registeredService != null && registeredService.getAccessStrategy.isServiceAccessAllowed) {
        logger.debug("Placing registered service [{}] with id [{}] in context scope", registeredService.getServiceId, registeredService.getId)
        WebUtils.putRegisteredService(context, registeredService)
      }else{
        Future.successful()
      }
    }else{
      Future.successful()
    }
    val bloatedServices = futureServices.flatMap(_ => WebUtils.putService(context, service))
    bloatedServices.onComplete(_ => logger.debug("we're done setting up the services in memcache"))
    bloatedServices
  }
}

object WebUtils {
  def getService(request: Request[AnyContent], cacheService: CacheService):Future[Option[Service]] = cacheService.get(Keys.serviceGenerator(request.cookies.get("sessionId").map(_.value).getOrElse(""),
    new Service{
    override def getId: String = request.cookies.get("serviceId").map(_.value).getOrElse("")

    /**
     * Sets the principal.
     *
     * @param principal the new principal
     */
    override def setPrincipal(principal: Principal): Service = ???
  }))

  private val CONST_PARAM_SERVICE: String = "service"
  private val CONST_PARAM_TARGET_SERVICE: String = "targetService"
  private val CONST_PARAM_TICKET: String = "ticket"
  private val CONST_PARAM_METHOD: String = "method"

  def getArgumentsExtractors(request: Request[AnyContent]):Service = {
    val id = request.cookies.get("serviceTicketId").map(_.value).getOrElse("")
    val targetService: String = request.getQueryString(CONST_PARAM_TARGET_SERVICE).getOrElse("")
    val service: String = request.getQueryString(CONST_PARAM_SERVICE).getOrElse("")
    val serviceAttribute: String = request.getQueryString(CONST_PARAM_SERVICE).asInstanceOf[String]
    val method: String = request.getQueryString(CONST_PARAM_METHOD).getOrElse("")
    val serviceToUse: String = {
      if (StringUtils.isNotBlank(targetService)) {
        targetService
      }
      else if (StringUtils.isNotBlank(service)) {
        service
      }
      else {
        serviceAttribute
      }
    }

    if (!StringUtils.isNotBlank(serviceToUse)) {
      return null
    }

    val artifactId: String = request.getQueryString(CONST_PARAM_TICKET).getOrElse("")

    return new SimpleWebApplicationServiceImpl(id, serviceToUse, artifactId, if (("POST" == request.method)) Response(Map(),POST) else Response(Map(),REDIRECT), None)
  }
  def putService(context: Request[AnyContent], service: Service)(implicit cacheService: CacheService) = {
    CacheOps.caching(context.getQueryString("sessionId").getOrElse(""))(service)
  }
  def putRegisteredService(context: Request[AnyContent], registeredService: RegisteredService)(implicit cacheService: CacheService) =
    CacheOps.caching(context.getQueryString("sessionId").getOrElse(""))(registeredService)
}



