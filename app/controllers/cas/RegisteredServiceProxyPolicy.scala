package controllers.cas

import java.net.URL

/**
 * Created by tash on 11/23/15.
 */
trait RegisteredServiceProxyPolicy{
  /**
   * Determines whether the service is allowed proxy
   * capabilities.
   *
   * @return true, if is allowed to proxy
   */
  def isAllowedToProxy: Boolean =false

  /**
   * Determines if the given proxy callback
   * url is authorized and allowed to
   * request proxy access.
   *
   * @param pgtUrl the pgt url
   * @return true, if url allowed.
   */
  def isAllowedProxyCallbackUrl(pgtUrl: URL): Boolean = false
}
class RefuseRegisteredServiceProxyPolicy extends RegisteredServiceProxyPolicy with Serializable