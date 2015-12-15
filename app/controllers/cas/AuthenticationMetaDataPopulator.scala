package controllers.cas

/**
 * Created by tash on 11/24/15.
 */
trait AuthenticationMetaDataPopulator{
  /**
   * Adds authentication metadata attributes on successful authentication of the given credential.
   *
   * @param builder Builder object that temporarily holds authentication metadata.
   * @param credential Successfully authenticated credential.
   */
  def populateAttributes(builder: AuthenticationBuilder, credential: Credentials)

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
