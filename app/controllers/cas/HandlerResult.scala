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



class DefaultHandlerResult(source: AuthenticationHandler, metaData: CredentialMetaData, p: Principal=null, warnings: List[MessageDescriptor] =null ) extends HandlerResult {
  /**
   * Gets handler name.
   *
   * @return the handler name
   */
  override def getHandlerName: String = source.getName

  /**
   * Gets principal.
   *
   * @return the principal
   */
  override def getPrincipal: Principal = p

  /**
   * Gets warnings.
   *
   * @return the warnings
   */
  override def getWarnings: List[MessageDescriptor] = warnings

  /**
   * Gets credential meta data.
   *
   * @return the credential meta data
   */
  override def getCredentialMetaData: CredentialMetaData = metaData
}