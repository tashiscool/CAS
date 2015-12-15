package controllers.cas

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
  def authenticate(credential: Credentials): HandlerResult

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
