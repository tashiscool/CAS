package controllers.cas

import java.net.URL

/**
 * Created by tash on 11/19/15.
 */
trait LogoutRequest extends Serializable{
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
  def setStatus(status: LogoutRequestStatus):LogoutRequest

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

case class LogoutRequestImpl(status: LogoutRequestStatus, ticketId:String, service: SingleLogoutService, logoutUrl: URL ) extends LogoutRequest {
  /**
   * Gets status of the request.
   *
   * @return the status
   */
  override def getStatus: LogoutRequestStatus = status

  /**
   * Sets status of the request.
   *
   * @param status the status
   */
  override def setStatus(status: LogoutRequestStatus): LogoutRequest = this.copy(status = status)

  /**
   * Gets logout url.
   *
   * @return the logout url
   */
  override def getLogoutUrl: URL = logoutUrl

  /**
   * Gets service.
   *
   * @return the service
   */
  override def getService: SingleLogoutService = service

  /**
   * Gets ticket id.
   *
   * @return the ticket id
   */
  override def getTicketId: String = ticketId
}