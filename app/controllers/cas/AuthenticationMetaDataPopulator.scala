package controllers.cas

import utils.scalautils.NullSafe

/**
 * Created by tash on 11/24/15.
 */
trait AuthenticationMetaDataPopulator{
  def builder:AuthenticationBuilder

  /**
   * Adds authentication metadata attributes on successful authentication of the given credential.
   *
   * @param builder Builder object that temporarily holds authentication metadata.
   * @param credential Successfully authenticated credential.
   */
  def populateAttributes(builder: AuthenticationBuilder, credential: Credentials):AuthenticationMetaDataPopulator

  /**
   * Determines whether the populator has the capability to perform tasks on the given credential.
   * In practice, the {@link #populateAttributes(AuthenticationBuilder, Credential)} needs to be able
   * to operate on said credentials only if the return result here is <code>true</code>.
   *
   * @param credential The credential to check.
   * @return True if populator supports the Credential, false otherwise.
   * @since 4.1.0
   */
  def supports(credential: Credentials): Boolean

}

case class ClientAuthenticationMetaDataPopulator(builder: AuthenticationBuilder, credential: Credentials) extends AuthenticationMetaDataPopulator {
  /***
  * The name of the client used to perform the authentication.
  */
  val CLIENT_NAME: String = "clientName"

  /**
  * {@inheritDoc}
  */
  def populateAttributes(builder: AuthenticationBuilder, credential: Credentials):ClientAuthenticationMetaDataPopulator = {
    val clientCredential: ClientCredential = credential.asInstanceOf[ClientCredential]
    this.copy(builder.addAttribute(CLIENT_NAME, clientCredential.theCredentials.id), credential = credential)
  }

  def supports (credential: Credentials): Boolean = {
    credential.isInstanceOf[ClientCredential]
  }
}
case class SuccessfulHandlerMetaDataPopulator(builder: AuthenticationBuilder, credential: Credentials) extends AuthenticationMetaDataPopulator{
  val SUCCESSFUL_AUTHENTICATION_HANDLERS = "successfulAuthenticationHandlers"

  /**
   * Adds authentication metadata attributes on successful authentication of the given credential.
   *
   * @param builder Builder object that temporarily holds authentication metadata.
   * @param credential Successfully authenticated credential.
   */
  override def populateAttributes(builder: AuthenticationBuilder, credential: Credentials): AuthenticationMetaDataPopulator = {
    this.copy(builder.addAttribute(SUCCESSFUL_AUTHENTICATION_HANDLERS, NullSafe(builder.getSuccesses.keySet).getOrElse(Set()) ), credential = credential)
  }

  /**
   * Determines whether the populator has the capability to perform tasks on the given credential.
   * In practice, the {@link #populateAttributes(AuthenticationBuilder, Credential)} needs to be able
   * to operate on said credentials only if the return result here is <code>true</code>.
   *
   * @param credential The credential to check.
   * @return True if populator supports the Credential, false otherwise.
   * @since 4.1.0
   */
  override def supports(credential: Credentials): Boolean = true
}