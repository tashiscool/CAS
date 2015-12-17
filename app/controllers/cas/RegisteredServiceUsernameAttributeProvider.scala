package controllers.cas

import org.slf4j.LoggerFactory

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
class DefaultRegisteredServiceUsernameProvider extends RegisteredServiceUsernameAttributeProvider with Serializable  {
  /**
   * Resolve the username that is to be returned to CAS clients.
   *
   * @param principal the principal
   * @param service the service for which attribute should be calculated
   * @return the username value configured for this service
   */
  override def resolveUsername(principal: Principal, service: Service): String = principal.getId
}

case class PrincipalAttributeRegisteredServiceUsernameProvider(usernameAttribute: String) extends RegisteredServiceUsernameAttributeProvider {
  val logger = LoggerFactory.getLogger(this.getClass)
  /**
   * Resolve the username that is to be returned to CAS clients.
   *
   * @param principal the principal
   * @param service the service for which attribute should be calculated
   * @return the username value configured for this service
   */
  override def resolveUsername(principal: Principal, service: Service): String = {
      var principalId: String = principal.getId
      if (principal.getAttributes.keys.exists(_ == this.usernameAttribute)) {
        principalId = principal.getAttributes.get(this.usernameAttribute).toString
      }
      else {
        logger.warn(s"Principal [${principalId}] did not have attribute [${this.usernameAttribute}] among attributes [${principal.getAttributes}] so CAS cannot provide the user attribute the service expects. CAS will instead return the default principal id [${principalId}]")
      }
      logger.debug(s"Principal id to return is [${principalId}]. The default principal id is [${principal.getId}].")
      principalId
  }
}