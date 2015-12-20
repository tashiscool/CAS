package controllers

import java.util.UUID

import com.google.inject.Inject
import controllers.cas._
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import utils.scalautils.{NullSafe, Keys, CacheOps, CacheService}
import play.api.libs.concurrent.Execution.Implicits._
import utils.scalautils.Keys._
import views.html._

import scala.concurrent.Future

case class LoginForm(username: String, password: String, lt: String, rememberMe: Boolean)


class Application @Inject()(val casService: CentralAuthenicationService, val servicesManager: ServicesManager, val cacheService: CacheService, val ticketIdGenerator: UniqueTicketIdGenerator)  extends Controller {

  val logger = LoggerFactory.getLogger(this.getClass)
  implicit val c: CacheService = cacheService

  //TODO: we can probably use implicits here for some cleanup -tk
  def getTgtId(request: Request[AnyContent]):String = request.cookies.get("tgt").map(_.value).getOrElse("")
  def getServiceLocation(request: Request[AnyContent]) = request.getQueryString("service").getOrElse("")
  def getGatewayLocation(request: Request[AnyContent]) = request.getQueryString("gateway").getOrElse("")
  def getRenew(request: Request[AnyContent]) = request.getQueryString("renew").getOrElse("")



  def TicketAction(gatewayRequestCheck: (Request[AnyContent]) => Future[Result], terminateSession: (Request[AnyContent]) => Future[Result],
                   hasServiceCheck: (Request[AnyContent]) => Future[Result] ) = Action.async { implicit request =>
    initialFlowSetupAction(request).flatMap{ _ =>
      val tgtId = getTgtId(request)
      if(StringUtils.isBlank(tgtId)){
        gatewayRequestCheck(request)
      }else {
        val someTicketFuture:Future[Option[BaseTicket]] = casService.getTicket(tgtId, classOf[BaseTicket])
        someTicketFuture.flatMap{ someTicket =>
          someTicket match{
            case Some(ticket) if(!ticket.isExpired) => hasServiceCheck(request)
            case _ =>terminateSession(request)
          }
        }
      }
    }
  }

  def indexMethod = Action {Ok}

  def hasServiceCheck(renewRequestCheck:(Request[AnyContent]) => Future[Result],
                      viewGenericLoginSuccess:(Request[AnyContent]) => Future[Result]
                      ):(Request[AnyContent]) => Future[Result] = { (request:Request[AnyContent]) =>
    val service = getServiceLocation(request)
    if(StringUtils.isNotBlank(service)) renewRequestCheck(request)
    else viewGenericLoginSuccess(request)
  }

  def renewRequestCheck(generateServiceTicket: (Request[AnyContent]) => Future[Result],
                        serviceAuthorizationCheck: (Request[AnyContent]) => Future[Result]):(Request[AnyContent]) => Future[Result] = { request:Request[AnyContent] =>
    val renew = getRenew(request)
    if(StringUtils.isNotBlank(renew)) serviceAuthorizationCheck(request) else generateServiceTicket(request)
  }

  def serviceAuthorizationCheck(generateLoginTicket: (Request[AnyContent]) => Future[Result]): (Request[AnyContent]) => Future[Result] ={request:Request[AnyContent] =>
    WebUtils.getService(request, cacheService).flatMap{
      case Some(service) =>
        servicesManager.getAllServices.flatMap{ case reqisteredServices =>
          if (reqisteredServices.isEmpty) {
            val msg: String = String.format("No service definitions are found in the service manager. " + "Service [%s] will not be automatically authorized to request authentication.", service.getId)
            logger.warn(msg)
            throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_EMPTY_SVC_MGMR, msg)
          }
          servicesManager.findServiceBy(service).flatMap{ registeredService =>
            if (registeredService == null) {
              val msg: String = String.format("ServiceManagement: Unauthorized Service Access. " + "Service [%s] is not found in service registry.", service.getId)
              logger.warn(msg)
              throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, msg)
            }
            if (!registeredService.getAccessStrategy.isServiceAccessAllowed) {
              val msg: String = String.format("ServiceManagement: Unauthorized Service Access. " + "Service [%s] is not enabled in service registry.", service.getId)
              logger.warn(msg)
              throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, msg)
            }else{
              generateLoginTicket(request)
            }
          }
        }
      case _ => generateLoginTicket(request)
    }

  }

  def generateLoginTicket = { request: Request[AnyContent] =>
    val loginTicket: String = this.ticketIdGenerator.getNewTicketId(TicketGrantingTicket.PREFIX)
    logger.debug(s"Generated login ticket ${loginTicket}")
    val credentialId = UUID.randomUUID().toString
    val newCredential = UsernamePasswordCredential(credentialId, "", "")
    //    WebUtils.saveCredentialsWithLoginTicket(newCredential,request, loginTicket, Ok(loginView))
    val result = Ok(loginView.apply(loginTicket)).withCookies(Cookie("credentials", newCredential.id),Cookie("tgt", loginTicket))
    WebUtils.saveCredentials(newCredential).map { _ =>
      result
    }
  }

  def viewGenericLoginSuccess = {request:Request[AnyContent] =>
    casService.getTicket(getTgtId(request), classOf[TicketGrantingTicket]).map{
      case Some(ticket)=> Ok(genericSuccessView(NullSafe(ticket.getAuthentication.getPrincipal).getOrElse(NullPrincipal())))
      case _ => Ok(genericSuccessView(NullPrincipal()))
    }
  }

  def login = {
    val k = serviceAuthorizationCheck(generateLoginTicket)
    val l = gatewayRequestCheck(gatewayServicesManagementCheck, k)
    val m = hasServiceCheck(renewRequestCheck(generateServiceTicket(generateLoginTicket),k), viewGenericLoginSuccess)
    TicketAction(l,terminateSession(l), m)
  }


  val credentialForm = Form(
    mapping(
      "username" -> text.verifying("invalid.email", { email => !email.isEmpty && email.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?") }),
      "password" -> text.verifying("required.password", { !_.isEmpty }),
      "lt" -> text,
      "rememberMe" -> optional(text).transform(_.isDefined, { b: Boolean => Some(b.toString) })
    )(LoginForm.apply)(LoginForm.unapply)
  )

  def realSubmit = Action.async{ implicit request =>
    credentialForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Redirect(controllers.routes.Application.login)),
      credentials => {
        if (!WebUtils.checkLoginTicketIfExists(request,credentials.lt)) {
          Future.successful(Redirect(controllers.routes.Application.login))
        } else if (WebUtils.isRequestAskingForServiceTicket(request, getServiceLocation(request), getTgtId(request))) {
          val serviceFuture: Future[Option[Service]] = WebUtils.getService(request, cacheService)
          val ticketGrantingTicket: String = WebUtils.getTicketGrantingTicketId(request)

          try {
            val credentialFuture: Future[Option[Credentials]] = Future.successful(Some(UsernamePasswordCredential(UUID.randomUUID().toString, credentials.username, credentials.password)))
            val foo = serviceFuture.flatMap { serviceO =>
              credentialFuture.flatMap { credentialO =>
                credentialO match {
                  case Some(credential) =>
                    serviceO match {
                      case Some(service) =>
                        val serviceTicketIdFuture: Future[ServiceTicket] = casService.grantServiceTicket(ticketGrantingTicket, service, List(credential))
                        serviceTicketIdFuture.flatMap { serviceTicketId =>
                          val attributes = service.getResponse.attributes.map{case(x,y) => (x,List(y).toSeq) }.+("ticket"->List(serviceTicketId.getId).toSeq)
                          if(service.getResponse.responseType == POST){
                            val newURL = s"${service.getOriginalUrl}?ticket=${serviceTicketId.getId}"
                            val futureResult:Future[Result] = Future.successful(Ok(autoredirector(newURL, attributes)))
                            Future.successful(Some(WebUtils.putServiceTicketInRequestScope(request, serviceTicketId, futureResult).map(_.withCookies(Cookie("serviceId",serviceTicketId.getId)))))
                          } else {
                            Future.successful(Some(WebUtils.putServiceTicketInRequestScope(request, serviceTicketId, Future.successful(Redirect(service.getOriginalUrl,attributes))).map(_.withCookies(Cookie("serviceId",serviceTicketId.getId)))))
                          }
                        }
                      case _ =>  val tgtTicketIdFuture: Future[TicketGrantingTicket] = casService.createTicketGrantingTicket(List(credential))
                        tgtTicketIdFuture.flatMap { case tgtTicketId:TicketGrantingTicketImpl =>
                          WebUtils.putTicketGrantingTicket(request, tgtTicketId).flatMap{ _ =>
                            Future.successful(Some(viewGenericLoginSuccess(request)))
                          }
                        }
                    }
                }
              }
            }
            foo.flatMap{ bar =>
              bar match{
                case Some(result) => result
                case _ => generateLoginTicket(request)
              }
            }
          }
        }else{
          val serviceFuture: Future[Option[Service]] = WebUtils.getService(request, cacheService)
          val ticketGrantingTicket: String = WebUtils.getTicketGrantingTicketId(request)

          try {
            val credentialFuture: Future[Option[Credentials]] = WebUtils.getCredential(request, cacheService)
            val foo = serviceFuture.flatMap { serviceO =>
              credentialFuture.flatMap { credentialO =>
                credentialO match {
                  case Some(credential) =>
                    serviceO match {
                      case Some(service) =>
                        val tgtTicketIdFuture: Future[TicketGrantingTicket] = casService.createTicketGrantingTicket(List(credential))
                        tgtTicketIdFuture.flatMap { tgtTicketId =>
                          val serviceTicketIdFuture: Future[ServiceTicket] = casService.grantServiceTicket(tgtTicketId.getId, service, List(credential).toSeq)
                          serviceTicketIdFuture.flatMap { serviceTicketId =>
                            val attributes = service.getResponse.attributes.map{case(x,y) => (x,List(y).toSeq) }.+("ticket"->List(serviceTicketId.getId).toSeq)
                            if(service.getResponse.responseType == POST){
                              val newURL = s"${service.getOriginalUrl}?ticket=${serviceTicketId.getId}"
                              val futureResult:Future[Result] = Future.successful(Ok(autoredirector(newURL, attributes)))
                              Future.successful(Some(WebUtils.putServiceTicketInRequestScope(request, serviceTicketId, futureResult)))
                            } else {
                              Future.successful(Some(WebUtils.putServiceTicketInRequestScope(request, serviceTicketId, Future.successful(Redirect(service.getOriginalUrl,attributes)))))
                            }
                          }
                        }
                    }
                }
              }
            }
            foo.flatMap{ bar =>
              bar match{
                case Some(result) => result
                case _ => generateLoginTicket(request)
              }
            }
          }
        }
      })
  }

  def generateServiceTicket(generateLoginTicket: (Request[AnyContent]) => Future[Result]):(Request[AnyContent]) => Future[Result] = { context:Request[AnyContent] =>
    val serviceFuture: Future[Option[Service]] = WebUtils.getService(context, cacheService)
    val ticketGrantingTicket: String = WebUtils.getTicketGrantingTicketId(context)

    try {
      val credentialFuture: Future[Option[Credentials]] = WebUtils.getCredential(context, cacheService)
      val foo = serviceFuture.flatMap{serviceO =>
          credentialFuture.flatMap{credentialO =>
            credentialO match { case Some(credential)=>
              serviceO match {case Some(service) =>
              val serviceTicketIdFuture: Future[ServiceTicket] = casService.grantServiceTicket(ticketGrantingTicket, service, List(credential))
              serviceTicketIdFuture.flatMap{serviceTicketId=>
                val attributes = service.getResponse.attributes.map{case(x,y) => (x,List(y).toSeq) }.+("ticket"->List(serviceTicketId.getId).toSeq)
                if(service.getResponse.responseType == POST){
                  val newURL = s"${service.getOriginalUrl}?ticket=${serviceTicketId.getId}"
                  val futureResult:Future[Result] = Future.successful(Ok(autoredirector(newURL, attributes)))
                  Future.successful(Some(WebUtils.putServiceTicketInRequestScope(context, serviceTicketId, futureResult)))
                } else {
                  Future.successful(Some(WebUtils.putServiceTicketInRequestScope(context, serviceTicketId, Future.successful(Redirect(service.getOriginalUrl,attributes)))))
                }
              }
              case _ => Future.successful(None)
            }
            case _ => Future.successful(None)
          }
        }
      }
      foo.flatMap{ bar =>
        bar match{
          case Some(result) => result
          case _ => generateLoginTicket(context)
        }
      }
    }
    catch {
      case e: Exception => {
        logger.error("Could not verify credentials to grant service ticket", e)
        generateLoginTicket(context)
      }
//      case e: TicketException => {
//        if (e.isInstanceOf[InvalidTicketException]) {
//          this.centralAuthenticationService.destroyTicketGrantingTicket(ticketGrantingTicket)
//        }
//        if (isGatewayPresent(context)) {
//          return result("gateway")
//        }
//      }
    }

  }

  def gatewayServicesManagementCheck = { request:Request[AnyContent] =>
   WebUtils.getService(request, cacheService).flatMap{ case Some(service) =>
     val futureMatch = servicesManager.matchesExistingService(service)
     futureMatch.flatMap{ x =>
       if(x) Future.successful(Ok)
       else Future.successful(BadRequest("doesn't match"))
     }
   case _ => Future.successful(BadRequest("no gateway/service found"))
   }
  }

  def gatewayRequestCheck(gatewayServicesManagementCheck: (Request[AnyContent]) => Future[Result],
                          serviceAuthorizationCheck: (Request[AnyContent]) => Future[Result])= { request:Request[AnyContent] =>
    if( StringUtils.isNotBlank(getServiceLocation(request)) && StringUtils.isNotBlank(getGatewayLocation(request)) )
      gatewayServicesManagementCheck(request)
    else
      serviceAuthorizationCheck(request)
  }

  def terminateSession(gatewayRequestCheck: (Request[AnyContent]) => Future[Result]) = { request:Request[AnyContent] =>
    WebUtils.clearTGT(gatewayRequestCheck(request))
  }

  def initialFlowSetupAction(request: Request[AnyContent]): Future[Any] = {
    val context = request
    val service: Service = WebUtils.getArgumentsExtractors(context)
    val futureServices = if (service != null) {
      logger.debug(s"Placing service in context scope: [${service.getId}]" )
      val registeredServiceFuture: Future[RegisteredService] = servicesManager.findServiceBy(service)
      registeredServiceFuture.flatMap{ registeredService =>
        if (registeredService != null && registeredService.getAccessStrategy.isServiceAccessAllowed) {
          logger.debug(s"Placing registered service [${registeredService.getServiceId}] with id [${registeredService.getId}] in context scope")
          WebUtils.putRegisteredService(context, registeredService)
        }else{
          Future.successful()
        }
      }
    }else{
      Future.successful()
    }
    val bloatedServices = futureServices.flatMap(_ => WebUtils.putService(context, service))
    bloatedServices.onComplete(_ => logger.debug("we're done setting up the services in memcache"))
    bloatedServices
  }

 val inMemoryMap = scala.collection.mutable.Map[String,String]()

  def demoLogin = Action{ implicit request=>
    val tgt = request.cookies.get("tgt").map(_.value).getOrElse("")
    if(StringUtils.isNotBlank(tgt)){
      val id3 = UUID.randomUUID().toString
      inMemoryMap.put(id3,request.body.asFormUrlEncoded.getOrElse(Map("username"->List("tashdid@gmail.com"))).get("username").getOrElse(List("tashdid@gmail.com")).headOption.getOrElse("tashdid@gmail.com"))
      val seq =Map("ticket"->List(id3).toSeq)
      val url = request.getQueryString("service").getOrElse(request.cookies.get("serviceLocation").map(_.value).getOrElse("google.com"))
      Redirect(url, seq)
    }else{
      val id = UUID.randomUUID().toString
      val id2 = UUID.randomUUID().toString
      Ok(demoLoginView(id)).withCookies(
        Cookie("serviceLocation", request.queryString("service").headOption.getOrElse("")),
        Cookie("tgtTry",id),Cookie("stTry", id2)
      )
    }

  }

  def demoDoLogin = Action {implicit request=>
    inMemoryMap.put(request.cookies.get("stTry").map(_.value).getOrElse("google.com"),request.body.asFormUrlEncoded.get.get("username").getOrElse(List("tashdid@gmail.com")).headOption.getOrElse("tashdid@gmail.com"))
    val seq =Map("ticket"->List(request.cookies.get("stTry").map(_.value).getOrElse("google.com")).toSeq)
    val url = request.cookies.get("serviceLocation").map(_.value).getOrElse("google.com")
    val id = UUID.randomUUID().toString
    val id2 = UUID.randomUUID().toString
    Redirect(url, seq).withCookies(Cookie("tgt",id),Cookie("st", id2))
  }

  def samlValidate(ticket:Option[String] = None) = Action{implicit request=>
    if(StringUtils.isNotBlank(ticket.getOrElse("")))
      Ok(sampleResponse(inMemoryMap.get(ticket.getOrElse("")).getOrElse(""))).withHeaders("Content-type"->"application/xml")
    else
      NotFound
  }
}

object WebUtils {
  def checkLoginTicketIfExists(request: Request[AnyContent], lt:String):Boolean = {
    val cookieTGT = request.cookies.get("tgt").map(_.value).getOrElse("")
    cookieTGT == lt
  }
  def isRequestAskingForServiceTicket(request: Request[AnyContent], service: String, ticketGrantingTicketId: String):Boolean = {
    (ticketGrantingTicketId != null && service != null)
  }

  def putServiceTicketInRequestScope(context: Request[AnyContent], serviceTicketId: ServiceTicket, eventualResult: Future[Result])(implicit cacheService: CacheService): Future[Result] = {
    serviceTicketId match { case serviceTicketId:ServiceTicketImpl=>
      CacheOps.caching("")(serviceTicketId).flatMap(_ => eventualResult).map(_.withCookies(Cookie("serviceTicketId",serviceTicketId.id)))
    case _ => CacheOps.caching("")(serviceTicketId).flatMap(_ => eventualResult).map(_.withCookies(Cookie("serviceTicketId",serviceTicketId.id)))
    }

  }

  def getCredential(request: Request[AnyContent], cacheService: CacheService):Future[Option[Credentials]] = {
    val uspwCredentials = UsernamePasswordCredential(request.cookies.get("credentials").map(_.value).getOrElse(""),"","")
    cacheService.get[UsernamePasswordCredential](Keys.uspwCredentialsGenerator(request.cookies.get("sessionId").map(_.value).getOrElse(""),uspwCredentials))
  }

  def clearTGT(eventualResult: Future[Result]) = eventualResult.map(_.withCookies(Cookie("tgt","")))

  def saveCredentialsWithLoginTicket(newCredential: UsernamePasswordCredential, request: Request[AnyContent], loginTicket: String, result: Result)(implicit cacheService: CacheService) = WebUtils.saveCredentials(newCredential).flatMap{_ =>
    WebUtils.putLoginTicket(request, loginTicket, result).map(_.withCookies(Cookie("credentials",newCredential.id)))
  }

  def saveCredentials(newCredential: UsernamePasswordCredential)(implicit cacheService: CacheService) = CacheOps.caching("")(newCredential)

  def getTicketGrantingTicketId(context: Request[AnyContent]): String = context.cookies.get("tgt").map(_.value).getOrElse("")

  def putLoginTicket(request: Request[AnyContent], loginTicket: String, result: Result) = Future.successful(result.withCookies(Cookie("tgt", loginTicket)))

  def getService(request: Request[AnyContent], cacheService: CacheService):Future[Option[Service]] = cacheService.get(Keys.serviceGenerator(request.cookies.get("sessionId").map(_.value).getOrElse(""),
    new Service{
      override def getResponse: Response = ???
      override def getOriginalUrl: String = ???
      override def getId: String = request.cookies.get("serviceId").map(_.value).getOrElse("")
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
    val serviceAttribute: String = request.getQueryString(CONST_PARAM_SERVICE).getOrElse("")
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
  def putTicketGrantingTicket(context: Request[AnyContent], ticketGrantingTicket: TicketGrantingTicketImpl)(implicit cacheService: CacheService) = {
    CacheOps.caching(context.getQueryString("sessionId").getOrElse(""))(ticketGrantingTicket)
  }
}

