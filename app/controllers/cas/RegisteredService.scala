package controllers.cas

import java.net.URL

/**
 * Interface for a service that can be registered by the Services Management
 * interface.
 *
 * @author Scott Battaglia
 * @since 3.1
 */
trait RegisteredService extends Serializable {
  val INITIAL_IDENTIFIER_VALUE: Long = Long.MaxValue
  /**
   * Get the proxy policy rules for this service.
   *
   * @return the proxy policy
   */
  def getProxyPolicy: RegisteredServiceProxyPolicy

  /**
   * The unique identifier for this service.
   *
   * @return the unique identifier for this service.
   */
  def getServiceId: String

  /**
   * The numeric identifier for this service. Implementations
   * are expected to initialize the id with the value of {@link #INITIAL_IDENTIFIER_VALUE}.
   * @return the numeric identifier for this service.
   */
  def getId: Long

  /**
   * Returns the name of the service.
   *
   * @return the name of the service.
   */
  def getName: String

  /**
   * Returns a short theme name. Services do not need to have unique theme
   * names.
   *
   * @return the theme name associated with this service.
   */
  def getTheme: String

  /**
   * Returns the description of the service.
   *
   * @return the description of the service.
   */
  def getDescription: String

  /**
   * Gets the relative evaluation order of this service when determining
   * matches.
   * @return Evaluation order relative to other registered services.
   *         Services with lower values will be evaluated for a match before others.
   */
  def getEvaluationOrder: Int

  /**
   * Sets the relative evaluation order of this service when determining
   * matches.
   * @param evaluationOrder the service evaluation order
   */
  def setEvaluationOrder(evaluationOrder: Int)

  /**
   * Get the name of the attribute this service prefers to consume as username.
   * @return an instance of { @link RegisteredServiceUsernameAttributeProvider}
   */
  def getUsernameAttributeProvider: RegisteredServiceUsernameAttributeProvider

  /**
   * Gets the set of handler names that must successfully authenticate credentials in order to access the service.
   * An empty set indicates that there are no requirements on particular authentication handlers; any will suffice.
   *
   * @return Non-null set of required handler names.
   */
  def getRequiredHandlers: Set[String]

  /**
   * Gets the access strategy that decides whether this registered
   * service is able to proceed with authentication requests.
   *
   * @return the access strategy
   */
  def getAccessStrategy: RegisteredServiceAccessStrategy

  /**
   * Returns whether the service matches the registered service.
   * <p>Note, as of 3.1.2, matches are case insensitive.
   *
   * @param service the service to match.
   * @return true if they match, false otherwise.
   */
  def matches(service: Service): Boolean

  /**
   * Clone this service.
   *
   * @return the registered service
   * @throws CloneNotSupportedException the clone not supported exception
   */
   override def clone: RegisteredService = throw new CloneNotSupportedException

  /**
   * Returns the logout type of the service.
   *
   * @return the logout type of the service.
   */
  def getLogoutType: LogoutType

  /**
   * Gets the attribute filtering policy to determine
   * how attributes are to be filtered and released for
   * this service.
   *
   * @return the attribute release policy
   */
  def getAttributeReleasePolicy: AttributeReleasePolicy

  /**
   * Gets the logo image associated with this service.
   * The image mostly is served on the user interface
   * to identify this requesting service during authentication.
   * @return URL of the image
   * @since 4.1
   */
  def getLogo: URL

  /**
   * Identifies the logout url that that will be invoked
   * upon sending single-logout callback notifications.
   * This is an optional setting. When undefined, the service
   * url as is defined by {@link #getServiceId()} will be used
   * to handle logout invocations.
   * @return the logout url for this service
   * @since 4.1
   */
  def getLogoutUrl: URL

  /**
   * Gets the public key associated with this service
   * that is used to authorize the request by
   * encrypting certain elements and attributes in
   * the CAS validation protocol response, such as
   * the PGT.
   * @return the public key instance used to authorize the request
   * @since 4.1
   */
  def getPublicKey: RegisteredServicePublicKey
}
