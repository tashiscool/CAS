package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait HandlerResult{
  /**
   * Gets handler name.
   *
   * @return the handler name
   */
  def getHandlerName: String

  /**
   * Gets credential meta data.
   *
   * @return the credential meta data
   */
  def getCredentialMetaData: CredentialMetaData

  /**
   * Gets principal.
   *
   * @return the principal
   */
  def getPrincipal: Principal

  /**
   * Gets warnings.
   *
   * @return the warnings
   */
  def getWarnings: List[MessageDescriptor]
}
