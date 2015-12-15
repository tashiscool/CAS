package controllers.cas

import org.slf4j.{LoggerFactory, Logger}


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
  def authenticate(credentials: Seq[Credentials]): Authentication
}
case class PolicyBasedAuthenticationManager(handlers: Seq[AuthenticationHandler], handlerResolverMap: Map[AuthenticationHandler, PrincipalResolver],
                                            authenticationMetaDataPopulators: List[AuthenticationMetaDataPopulator],
                                            authenticationPolicy: AuthenticationPolicy) extends AuthenticationManager{
  val logger = LoggerFactory.getLogger(this.getClass)

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
  override def authenticate(credentials: Seq[Credentials]): Authentication = {
    val builder: AuthenticationBuilder = authenticateInternal(credentials)
    val authentication: Authentication = builder.build
    val principal: Principal = authentication.getPrincipal
    if (principal.isInstanceOf[NullPrincipal]) {
      throw new RuntimeException(s"authentication is invalid $authentication")
    }
    import scala.collection.JavaConversions._
    for (result <- authentication.getSuccesses.values) {
      builder.addAttribute(AUTHENTICATION_METHOD_ATTRIBUTE, result.getHandlerName)
    }

    logger.info(s"Authenticated ${principal} with credentials ${credentials.toArray}.")
    logger.debug(s"Attribute map for ${principal.getId}: ${principal.getAttributes}")
    import scala.collection.JavaConversions._
    for (populator <- this.authenticationMetaDataPopulators) {
      for (credential <- credentials) {
        if (populator.supports(credential)) {
          populator.populateAttributes(builder, credential)
        }
      }
    }

    builder.build
  }
  def authenticateInternal(credentials: Seq[Credentials]):AuthenticationBuilder =  {
    val builder: AuthenticationBuilder = new DefaultAuthenticationBuilder(NullPrincipal.apply(Map()))
    for (c <- credentials) {
      builder.addCredential(new BasicCredentialMetaData(c))
    }
    var found: Boolean = false
    var principal: Principal = null
    var resolver: PrincipalResolver = null
    for (credential <- credentials) {
      found = false
      import scala.collection.JavaConversions._
      for (entry <- this.handlerResolverMap.entrySet) {
        val handler: AuthenticationHandler = entry.getKey
        if (handler.supports(credential)) {
          found = true
          try {
            val result: HandlerResult = handler.authenticate(credential)
            builder.addSuccess(handler.getName, result)
            logger.info(s"${handler.getName} successfully authenticated ${credential}")
            resolver = entry.getValue
            if (resolver == null) {
              principal = result.getPrincipal
              logger.debug(s"No resolver configured for ${handler.getName}. Falling back to handler principal ${principal}")
            }
            else {
              principal = resolvePrincipal(handler.getName, resolver, credential).get//TODO: fix this -tk
            }
            if (principal != null) {
              builder.setPrincipal(principal)
            }
            if (this.authenticationPolicy.isSatisfiedBy(builder.build)) {
              return builder
            }
          }
          catch {
            case e: Exception => {
              logger.error(s"${ handler.getName}: ${e.getMessage}  (Details: ${e.getCause.getMessage})")
              builder.addFailure(handler.getName, e.getClass)
            }
          }
        }
      }
      if (!found) {
        logger.warn(s"Cannot find authentication handler that supports ${credential}, which suggests a configuration problem.")
      }
    }
    if (builder.getSuccesses.isEmpty) {
      throw new AuthenticationException(builder.getFailures, builder.getSuccesses)
    }
    if (!this.authenticationPolicy.isSatisfiedBy(builder.build)) {
      throw new AuthenticationException(builder.getFailures, builder.getSuccesses)
    }
    builder
  }

  def resolvePrincipal(handlerName: String, resolver: PrincipalResolver, credential: Credentials):Option[Principal] = {
    if (resolver.supports(credential)) {
      try {
        val p: Principal = resolver.resolve(credential)
        logger.debug(s"${resolver} resolved ${p} from ${credential}")
        Some(p)
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
    return None
  }
}

case class AuthenticationException(failures: Map[String, Class[_ <: Exception]] = Map(),
                                   successes:Map[String, HandlerResult] = Map()) extends RuntimeException






