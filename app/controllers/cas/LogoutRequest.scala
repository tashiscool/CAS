package controllers.cas

import java.net.URL

/**
 * Created by tash on 11/19/15.
 */
trait LogoutRequest {
  /**
   * Gets status of the request.
   *
   * @return the status
   */
  def getStatus: LogoutRequestStatus

  /**
   * Sets status of the request.
   *
   * @param status the status
   */
  def setStatus(status: LogoutRequestStatus)

  /**
   * Gets ticket id.
   *
   * @return the ticket id
   */
  def getTicketId: String

  /**
   * Gets service.
   *
   * @return the service
   */
  def getService: SingleLogoutService

  /**
   * Gets logout url.
   *
   * @return the logout url
   */
  def getLogoutUrl: URL
}
