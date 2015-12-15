package controllers.cas

/**
 * Created by tash on 11/23/15.
 */
trait AttributeReleasePolicy{
  /**
   * Is authorized to release credential password?
   *
   * @return the boolean
   */
  def isAuthorizedToReleaseCredentialPassword: Boolean

  /**
   * Is authorized to release proxy granting ticket?
   *
   * @return the boolean
   */
  def isAuthorizedToReleaseProxyGrantingTicket: Boolean

  /**
   * Sets the attribute filter.
   *
   * @param filter the new attribute filter
   */
  def setAttributeFilter(filter: Map[String, AnyRef] => Map[String, AnyRef])

  /**
   * Gets the attributes, having applied the filter.
   *
   * @param p the principal that contains the resolved attributes
   * @return the attributes
   */
  def getAttributes(p: Principal): Map[String, AnyRef]
}
