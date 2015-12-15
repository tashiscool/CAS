package controllers.cas

/**
 * Created by tash on 11/23/15.
 */
trait RegisteredServiceAccessStrategy{
  /**
   * Verify is the service is enabled and recognized by CAS.
   *
   * @return true/false if service is enabled
   */
  def isServiceAccessAllowed: Boolean

  /**
   * Assert that the service can participate in sso.
   *
   * @return true/false if service can participate in sso
   */
  def isServiceAccessAllowedForSso: Boolean

  /**
   * Verify authorization policy by checking the pre-configured rules
   * that may depend on what the principal might be carrying.
   *
   * @param principalAttributes the principal attributes. Rather than passing the principal
   *                            directly, we are only allowing principal attributes
   *                            given they may be coming from a source external to the principal
   *                            itself. (Cached principal attributes, etc)
   * @return true/false if service access can be granted to principal
   */
  def doPrincipalAttributesAllowServiceAccess(principalAttributes: Map[String, AnyRef]): Boolean
}
