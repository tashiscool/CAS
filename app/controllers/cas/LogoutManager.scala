package controllers.cas

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
