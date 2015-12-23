package controllers.cas

import java.util.Date

import org.joda.time.DateTime

/**
 * Created by tash on 11/19/15.
 */
trait Authentication extends Serializable{
  /**
   * Method to obtain the Principal.
   *
   * @return a Principal implementation
   */
  def getPrincipal: Principal

  /**
   * Method to retrieve the timestamp of when this Authentication object was
   * created.
   *
   * @return the date/time the authentication occurred.
   */
  def getAuthenticationDate: Date

  /**
   * Attributes of the authentication (not the Principal).
   *
   * @return the map of attributes.
   */
  def getAttributes: Map[String, Any]

  /**
   * Gets a list of metadata about the credentials supplied at authentication time.
   *
   * @return Non-null list of supplied credentials represented as metadata that should be considered safe for
   *         long-term storage (e.g. serializable and secure with respect to credential disclosure). The order of items in
   *         the returned list SHOULD be the same as the order in which the source credentials were presented and subsequently
   *         processed.
   */
  def getCredentials: List[CredentialMetaData]

  /**
   * Gets a map describing successful authentications produced by {@link AuthenticationHandler} components.
   *
   * @return Map of handler names to successful authentication result produced by that handler.
   */
  def getSuccesses: Map[String, HandlerResult]

  /**
   * Gets a map describing failed authentications. By definition the failures here were not sufficient to prevent
   * authentication.
   *
   * @return Map of authentication handler names to the authentication errors produced on attempted authentication.
   */
  def getFailures: Map[String, Class[_ <: Exception]]
}

case class ImmutableAuthentication(date: DateTime, credentials: List[CredentialMetaData], principal: Principal, attributes: Map[String, Any], successes: Map[String, HandlerResult], failures: Map[String, Class[_ <: Exception]]) extends Authentication {
  override def getPrincipal: Principal = principal

  override def getAttributes: Map[String, Any] = attributes

  override def getAuthenticationDate: Date = new Date(date.getMillis)

  override def getCredentials: List[CredentialMetaData] = credentials

  override def getSuccesses: Map[String, HandlerResult] = successes

  override def getFailures: Map[String, Class[_ <: Exception]] = failures
}