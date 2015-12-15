package controllers.cas

/**
 * Created by tash on 11/23/15.
 */
trait RegisteredServiceUsernameAttributeProvider{
  /**
   * Resolve the username that is to be returned to CAS clients.
   *
   * @param principal the principal
   * @param service the service for which attribute should be calculated
   * @return the username value configured for this service
   */
  def resolveUsername(principal: Principal, service: Service): String
}
