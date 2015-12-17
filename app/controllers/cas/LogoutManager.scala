package controllers.cas

import java.net.URL

/**
 * Created by tash on 11/23/15.
 */
trait LogoutManager {
/**
 * Perform a back channel logout for a given ticket granting ticket and returns all the logout requests.
 *
 * @param ticket a given ticket granting ticket.
 * @return all logout requests.
 */
def performLogout (ticket: TicketGrantingTicket): List[LogoutRequest]

/**
 * Create a logout message for front channel logout.
 *
 * @param logoutRequest the logout request.
 * @return a front SAML logout message.
 */
def createFrontChannelLogoutMessage (logoutRequest: LogoutRequest): String
}

class LogoutManagerImpl extends LogoutManager{
  val singleLogoutCallbacksDisabled = true

  def handleLogoutForSloService(service: SingleLogoutService, getKey: String): LogoutRequest = LogoutRequestImpl(NOT_ATTEMPTED, getKey, service, new URL("http://google.com"))

  /**
   * Perform a back channel logout for a given ticket granting ticket and returns all the logout requests.
   *
   * @param ticket a given ticket granting ticket.
   * @return all logout requests.
   */
  override def performLogout(ticket: TicketGrantingTicket): List[LogoutRequest] = {
    val services: Map[String, Service] = ticket.getServices
    val logoutRequests: List[LogoutRequest] = List[LogoutRequest]()
    // if SLO is not disabled
    if (!this.singleLogoutCallbacksDisabled) {
      import scala.collection.JavaConversions._
      for (entry <- services.entrySet) {
        val service: Service = entry.getValue
        if (service.isInstanceOf[SingleLogoutService]) {
          val logoutRequest: LogoutRequest = handleLogoutForSloService(service.asInstanceOf[SingleLogoutService], entry.getKey)
          if (logoutRequest != null) {

            logoutRequests.add(logoutRequest)
          }
        }
      }
    }

    return logoutRequests
  }

  /**
   * Create a logout message for front channel logout.
   *
   * @param logoutRequest the logout request.
   * @return a front SAML logout message.
   */
  override def createFrontChannelLogoutMessage(logoutRequest: LogoutRequest): String = ???
}