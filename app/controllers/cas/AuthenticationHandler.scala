package controllers.cas

import java.security.GeneralSecurityException
import javax.security.auth.login.{FailedLoginException, AccountNotFoundException}

import models.dao.sapi.{UserCrendentialDaoReactive, UserDaoReactive}
import org.apache.commons.lang3.StringUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

/**
 * Created by tash on 11/24/15.
 */
trait AuthenticationHandler {
  /**
   * Authenticates the given credential. There are three possible outcomes of this process, and implementers
   * MUST adhere to the following contract:
   *
   * <ol>
   * <li>Success -- return {@link HandlerResult}</li>
   * <li>Failure -- throw {@link GeneralSecurityException}</li>
   * <li>Indeterminate -- throw {@link PreventedException}</li>
   * </ol>
   *
   * @param credential The credential to authenticate.
   *
   * @return A result object containing metadata about a successful authentication event that includes at
   *         a minimum the name of the handler that authenticated the credential and some credential metadata.
   *         The following data is optional:
   *         <ul>
   *         <li>{ @link org.jasig.cas.authentication.principal.Principal}</li>
   *                     <li>Messages issued by the handler about the credential (e.g. impending password expiration warning)</li>
   *                     </ul>
   *
   */
  def authenticate(credential: Credentials): Future[HandlerResult]

  /**
   * Determines whether the handler has the capability to authenticate the given credential. In practical terms,
   * the {@link #authenticate(Credential)} method MUST be capable of processing a given credential if
   * <code>supports</code> returns true on the same credential.
   *
   * @param credential The credential to check.
   *
   * @return True if the handler supports the Credential, false otherwise.
   */
  def supports(credential: Credentials): Boolean

  /**
   * Gets a unique name for this authentication handler within the Spring context that contains it.
   * For implementations that allow setting a unique name, deployers MUST take care to ensure that every
   * handler instance has a unique name.
   *
   * @return Unique name within a Spring context.
   */
  def getName: String
}

sealed abstract class AbstractAuthenticationHandler(principalFactory: PrincipalFactory = new DefaultPrincipalFactory, name : String = "") extends AuthenticationHandler {

def getName: String = {
  if (StringUtils.isNotBlank(name)) this.name else getClass.getSimpleName
}

}

case class PrincipalBearingCredentialsAuthenticationHandler(principalFactory: PrincipalFactory = new DefaultPrincipalFactory, name : String = "") extends AbstractAuthenticationHandler {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def authenticate(credential: Credentials): Future[HandlerResult] = {
    logger.debug("Trusting credential for: {}", credential)
    return Future.successful(new DefaultHandlerResult(this, credential.asInstanceOf[PrincipalBearingCredential], this.principalFactory.createPrincipal(credential.id)))
  }

  def supports(credential: Credentials): Boolean = {
    return credential.isInstanceOf[PrincipalBearingCredential]
  }
  override def getName: String = {
    if (this.name != null) this.name else getClass.getSimpleName
  }
  /**
   * Sets the authentication handler name. Authentication handler names SHOULD be unique within an
   * {@link org.jasig.cas.authentication.AuthenticationManager}, and particular implementations
   * may require uniqueness. Uniqueness is a best
   * practice generally.
   *
   * @param name Handler name.
   */
  def setName (name: String):AbstractAuthenticationHandler=this.copy(name = name)

  /**
   * Sets principal factory to create principal objects.
   *
   * @param principalFactory the principal factory
   */
  def setPrincipalFactory (principalFactory: PrincipalFactory):AbstractAuthenticationHandler = {
    this.copy(principalFactory = principalFactory)
  }
}

trait AbstractPreAndPostProcessingAuthenticationHandler extends AbstractAuthenticationHandler {
  /** Instance of logging for subclasses. */
  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Template method to perform arbitrary pre-authentication actions.
   *
   * @param credential the Credential supplied
   * @return true if authentication should continue, false otherwise.
   */
  protected def preAuthenticate(credential: Credentials): Boolean = {
    return true
  }

  /**
   * Template method to perform arbitrary post-authentication actions.
   *
   * @param credential the supplied credential
   * @param result the result of the authentication attempt.
   *
   * @return An authentication handler result that MAY be different or modified from that provided.
   */
  protected def postAuthenticate(credential: Credentials, result: Future[HandlerResult]): Future[HandlerResult] = {
    result
  }

  /**
   * {@inheritDoc}
   **/
  def authenticate(credential: Credentials): Future[HandlerResult] = {
    if (!preAuthenticate(credential)) {
      throw new RuntimeException("failed Login")
    }
    return postAuthenticate(credential, doAuthentication(credential))
  }

  /**
   * Performs the details of authentication and returns an authentication handler result on success.
   *
   *
   * @param credential Credential to authenticate.
   *
   * @return Authentication handler result on success.
   *
   * @throws GeneralSecurityException On authentication failure that is thrown out to the caller of
   *                                  { @link #authenticate(org.jasig.cas.authentication.Credential)}.
   */
  protected def doAuthentication(credential: Credentials): Future[HandlerResult]

  /**
   * Helper method to construct a handler result
   * on successful authentication events.
   *
   * @param credential the credential on which the authentication was successfully performed.
   *                   Note that this credential instance may be different from what was originally provided
   *                   as transformation of the username may have occurred, if one is in fact defined.
   * @param principal the resolved principal
   * @param warnings the warnings
   * @return the constructed handler result
   */
  protected def createHandlerResult(credential: Credentials, principal: Principal, warnings: List[MessageDescriptor]): HandlerResult = {
    return new DefaultHandlerResult(this, new BasicCredentialMetaData(credential), principal, warnings)
  }
}


case class QueryDatabaseAuthenticationHandler(principalFactory: PrincipalFactory, passwordEncoder: PasswordEncoder = new PlainTextPasswordEncoder,
  principalNameTransformer: PrincipalNameTransformer = new NoOpPrincipalNameTransformer, passwordPolicyConfiguration: PasswordPolicyConfiguration = null,
                                              credentialsReactive: UserCrendentialDaoReactive, name:String = "") extends AbstractPreAndPostProcessingAuthenticationHandler {
  @NotNull private var sql: String = null

  def failedUserLookup: HandlerResult = throw new AccountNotFoundException("User/password not valid.")

  /**
   * Authenticates a username/password credential by an arbitrary strategy.
   *
   * @param credential the credential object bearing the transformed username and password.
   *
   * @return HandlerResult resolved from credential on authentication success or null if no principal could be resolved
   *         from the credential.
   *
   * @throws GeneralSecurityException On authentication failure.
   */
  protected def authenticateUsernamePasswordInternal(credential: UsernamePasswordCredential): Future[HandlerResult] = {
    //TODO: this is generally a bad call, we should store credentials in their own collection -tk
    credentialsReactive.getUserCrendentialByQ(credential.userName).map{
      case Some(user) =>
        if(user.lookupValues("password") == credential.password)
          createHandlerResult(credential, principalFactory.createPrincipal(credential.userName), null)
        else
          failedUserLookup
      case _ => failedUserLookup
    }


  }

  /**
   * @param sql The sql to set.
   */
  def setSql(sql: String) {
    this.sql = sql
  }

  /**
   * {@inheritDoc}
   **/
  protected def doAuthentication(credential: Credentials): Future[HandlerResult] = {
    val userPass: UsernamePasswordCredential = credential.asInstanceOf[UsernamePasswordCredential]
    if (userPass.userName == null) {
      throw new AccountNotFoundException("Username is null.")
    }
    val transformedUsername: String = this.principalNameTransformer.transform(userPass.userName)
    if (transformedUsername == null) {
      throw new AccountNotFoundException("Transformed username is null.")
    }

    return authenticateUsernamePasswordInternal(userPass.setUsername(transformedUsername))
  }

  /**
   * Method to return the PasswordEncoder to be used to encode passwords.
   *
   * @return the PasswordEncoder associated with this class.
   */
  protected def getPasswordEncoder: PasswordEncoder = {
    return this.passwordEncoder
  }

  protected def getPrincipalNameTransformer: PrincipalNameTransformer = {
    return this.principalNameTransformer
  }

  protected def getPasswordPolicyConfiguration: PasswordPolicyConfiguration = {
    return this.passwordPolicyConfiguration
  }

  /**
   * Sets the PasswordEncoder to be used with this class.
   *
   * @param passwordEncoder the PasswordEncoder to use when encoding
   *                        passwords.
   */
  def setPasswordEncoder(passwordEncoder: PasswordEncoder):QueryDatabaseAuthenticationHandler = {
    this.copy(passwordEncoder = passwordEncoder)
  }

  def setPrincipalNameTransformer(principalNameTransformer: PrincipalNameTransformer):QueryDatabaseAuthenticationHandler= {
    this.copy(principalNameTransformer = principalNameTransformer)
  }

  def setPasswordPolicyConfiguration(passwordPolicyConfiguration: PasswordPolicyConfiguration):QueryDatabaseAuthenticationHandler= {
    this.copy(passwordPolicyConfiguration = passwordPolicyConfiguration)
  }

  /**
   * {@inheritDoc}
   * @return True if credential is a { @link UsernamePasswordCredential}, false otherwise.
   */
  def supports(credential: Credentials): Boolean = {
    return credential.isInstanceOf[UsernamePasswordCredential]
  }

  /**
   * Sets the authentication handler name. Authentication handler names SHOULD be unique within an
   * {@link org.jasig.cas.authentication.AuthenticationManager}, and particular implementations
   * may require uniqueness. Uniqueness is a best
   * practice generally.
   *
   * @param name Handler name.
   */
  def setName(name: String):QueryDatabaseAuthenticationHandler=this.copy(name = name)

  /**
   * Sets principal factory to create principal objects.
   *
   * @param principalFactory the principal factory
   */
  def setPrincipalFactory (principalFactory: PrincipalFactory):QueryDatabaseAuthenticationHandler = {
    this.copy(principalFactory = principalFactory)
  }
}

trait PasswordEncoder{
  def encode (password: String): String
}

trait PrincipalNameTransformer {
  def transform(getUsername: String): String = getUsername
}

trait PasswordPolicyConfiguration

class PlainTextPasswordEncoder extends PasswordEncoder {
  def encode (password: String): String = {
    password
  }
}

class NoOpPrincipalNameTransformer extends PrincipalNameTransformer {
  override def transform (formUserId: String): String = {
    formUserId
  }
}

