package controllers.cas

import com.google.inject.Inject
import org.slf4j.{LoggerFactory, Logger}
import scala.collection.JavaConversions._
import scala.concurrent.Future


/**
 * Created by tash on 11/23/15.
 */
trait AuthenticationManager{
  /** Authentication method attribute name. **/
  val AUTHENTICATION_METHOD_ATTRIBUTE: String = "authenticationMethod"

  /**
   * Authenticates the provided credentials. On success, an {@link Authentication} object
   * is returned containing metadata about the result of each authenticated credential.
   * Note that a particular implementation may require some or all credentials to be
   * successfully authenticated. Failure to authenticate is considered an exceptional case, and
   * an AuthenticationException is thrown.
   *
   * @param credentials One or more credentials to authenticate.
   *
   * @return Authentication object on success that contains metadata about credentials that were authenticated.
   *
   * @throws AuthenticationException On authentication failure. The exception contains details
   *                                 on each of the credentials that failed to authenticate.
   */
  def authenticate(credentials: Seq[Credentials]): Future[Authentication]
}
case class PolicyBasedAuthenticationManager @Inject() (handlers: Seq[AuthenticationHandler], handlerResolverMap: Map[AuthenticationHandler, PrincipalResolver],
                                            authenticationMetaDataPopulators: List[AuthenticationMetaDataPopulator]) extends AuthenticationManager{
  val logger = LoggerFactory.getLogger(this.getClass)

  val serviceContextAuthenticationPolicyFactory = new AcceptAnyAuthenticationPolicyFactory
  /**
   * Authenticates the provided credentials. On success, an {@link Authentication} object
   * is returned containing metadata about the result of each authenticated credential.
   * Note that a particular implementation may require some or all credentials to be
   * successfully authenticated. Failure to authenticate is considered an exceptional case, and
   * an AuthenticationException is thrown.
   *
   * @param credentials One or more credentials to authenticate.
   *
   * @return Authentication object on success that contains metadata about credentials that were authenticated.
   *
   * @throws AuthenticationException On authentication failure. The exception contains details
   *                                 on each of the credentials that failed to authenticate.
   */
  override def authenticate(credentials: Seq[Credentials]): Future[Authentication] = {
    val authenticationF: Future[AuthenticationBuilder] = authenticateInternal(credentials)
    authenticationF.map{builder=>
      val authentication = builder.build
      val principal: Principal = authentication.getPrincipal
      if (principal.isInstanceOf[NullPrincipal]) {
        throw new RuntimeException(s"authentication is invalid $authentication")
      }
      import scala.collection.JavaConversions._
      val updatedBuilder = authentication.getSuccesses.values.foldLeft(builder) {case (builder,result) =>
        builder.addAttribute(AUTHENTICATION_METHOD_ATTRIBUTE, result.getHandlerName)
      }

      logger.info(s"Authenticated ${principal} with credentials ${credentials.toArray}.")
      logger.debug(s"Attribute map for ${principal.getId}: ${principal.getAttributes}")

      val populatedMetadata = authenticationMetaDataPopulators.map{populator =>
        credentials.map{credential =>
          if (populator.supports(credential)) {
            populator.populateAttributes(updatedBuilder, credential)
          }
          else populator
        }
      }.flatten
      populatedMetadata.find(x => x.builder.build != null).map(_.builder.build).getOrElse(authentication)
    }

  }
  def authenticateInternal(credentials: Seq[Credentials]):Future[AuthenticationBuilder] =  {
    val builder: Future[AuthenticationBuilder] = Future.successful(new DefaultAuthenticationBuilder(NullPrincipal.apply(Map())))
    val authenticationPolicy = serviceContextAuthenticationPolicyFactory.createPolicy(null)
    val credentialedBuilder = credentials.foldLeft(builder){(b,credential) => b.map(_.addCredential(new BasicCredentialMetaData(credential))) }
    var found: Boolean = false
    credentials.foldLeft(credentialedBuilder){(builderF,credential) =>
      //TODO: this can be better written using for yield -tk
      builderF.flatMap{builder =>
        found = false
       handlerResolverMap.map{ case (handler,resolver)  =>
          if (handler.supports(credential)) {
            found = true
            try {
              val resultF: Future[HandlerResult] = handler.authenticate(credential)
              val authF: Future[AuthenticationBuilder] = resultF.flatMap{result =>
                val successedBuilder = builder.addSuccess(handler.getName, result)
                logger.info(s"${handler.getName} successfully authenticated ${credential}")
                val principalF: Future[Option[Principal]] = if (resolver == null) {
                  logger.debug(s"No resolver configured for ${handler.getName}. Falling back to handler result ${result}")
                  Future.successful(Option(result.getPrincipal))
                }
                else {
                  resolvePrincipal(handler.getName, resolver, credential)//TODO: fix this -tk
                }
                principalF.map{
                  case Some(principal) =>
                    val principaledBuilder = if (principal != null) {
                      successedBuilder.setPrincipal(principal)
                    } else{
                      successedBuilder
                    }
                    if (authenticationPolicy.isSatisfiedBy(builder.build)) {
                      principaledBuilder
                    }else{
                      principaledBuilder
                    }
                  case _ => successedBuilder
                }
              }
              authF
            }
            catch {
              case e: Exception => {
                logger.error(s"${ handler.getName}: ${e.getMessage}  (Details: ${e.getCause.getMessage})")
                Future.successful(builder.addFailure(handler.getName, e.getClass))
              }
            }
          }else{
            Future.successful(builder)
          }
        }.head
      }
    }.map{ builder =>
      if (!found) {
        logger.warn(s"Cannot find authentication handler that supports ${credentials}, which suggests a configuration problem.")
      }
      if (builder.getSuccesses.isEmpty) {
        throw new AuthenticationException(builder.getFailures, builder.getSuccesses)
      }
      val auth = builder.build
      if (authenticationPolicy.isSatisfiedBy(auth)) {
        throw new AuthenticationException(builder.getFailures, builder.getSuccesses)
      }
      builder
    }
  }

  def resolvePrincipal(handlerName: String, resolver: PrincipalResolver, credential: Credentials):Future[Option[Principal]] = {
    if (resolver.supports(credential)) {
      try {
        val pF: Future[Principal] = resolver.resolve(credential)
        pF.map{p => logger.debug(s"${resolver} resolved ${p} from ${credential}"); Option(p)}
      }
      catch {
        case e: Exception => {
          logger.error(s"${resolver} failed to resolve principal from ${credential}", e)
        }
      }
    }
    else {
      logger.warn(s"${handlerName} is configured to use ${resolver} but it does not support ${credential}, which suggests a configuration problem.")
    }
    return Future.successful(None)
  }
}

case class AuthenticationException(failures: Map[String, Class[_ <: Exception]] = Map(),
                                   successes:Map[String, HandlerResult] = Map()) extends RuntimeException






