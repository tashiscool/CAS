package controllers.cas

import java.util
import java.util.Date

import org.joda.time.DateTime

/**
 * Created by tash on 11/24/15.
 */
trait AuthenticationBuilder {
  /**
   * Gets the authenticated principal.
   *
   * @return Principal.
   */
  def getPrincipal: Principal

  /**
   * Sets the principal returns this instance.
   *
   * @param p Authenticated principal.
   *
   * @return This builder instance.
   */
  def setPrincipal(p: Principal): AuthenticationBuilder

  /**
   * Adds metadata about a credential presented for authentication.
   *
   * @param credential Credential metadata.
   *
   * @return This builder instance.
   */
  def addCredential(credential: CredentialMetaData): AuthenticationBuilder

  /**
   * Adds an authentication metadata attribute key-value pair.
   *
   * @param key Authentication attribute key.
   * @param value Authentication attribute value.
   *
   * @return This builder instance.
   */
  def addAttribute(key: String, value: Any): AuthenticationBuilder

  /**
   * Gets the authentication success map.
   *
   * @return Non-null map of handler names to successful handler authentication results.
   */
  def getSuccesses: Map[String, HandlerResult]

  /**
   * Adds an authentication success to the map of handler names to successful authentication handler results.
   *
   * @param key Authentication handler name.
   * @param value Successful authentication handler result produced by handler of given name.
   *
   * @return This builder instance.
   */
  def addSuccess(key: String, value: HandlerResult): AuthenticationBuilder

  /**
   * Sets the authentication date and returns this instance.
   *
   * @param d Authentication date.
   *
   * @return This builder instance.
   */
  def setAuthenticationDate(d: java.util.Date): AuthenticationBuilder

  /**
   * Creates an immutable authentication instance from builder data.
   *
   * @return Immutable authentication.
   */
  def build: Authentication

  /**
   * Gets the authentication failure map.
   *
   * @return Non-null authentication failure map.
   */
  def getFailures: Map[String, Class[_ <: Exception]]

  /**
   * Adds an authentication failure to the map of handler names to the authentication handler failures.
   *
   * @param key Authentication handler name.
   * @param value Exception raised on handler failure to authenticate credential.
   *
   * @return This builder instance.
   */
  def addFailure(key: String, value: Class[_ <: Exception]): AuthenticationBuilder

  /**
   * Sets the authentication metadata attributes.
   *
   * @param attributes Non-null map of authentication metadata attributes.
   *
   * @return This builder instance.
   */
  def setAttributes(attributes: Map[String, Any]): AuthenticationBuilder
}
object DefaultAuthenticationBuilder{
  def newInstance (source : Authentication):AuthenticationBuilder = {
    val builder : DefaultAuthenticationBuilder = new DefaultAuthenticationBuilder(source.getPrincipal,source.getCredentials,source.getAttributes,source.getSuccesses,source.getFailures,new DateTime(source.getAuthenticationDate.getTime) )
    builder
  }
}
case class DefaultAuthenticationBuilder(p: Principal,credentials: List[CredentialMetaData] = List(),
                                        attributes: Map[String, Any] = Map(), successes:Map[String, HandlerResult] = Map(),
                                        failures: Map[String, Class[_ <: Exception]] = Map(), authenticationDate: DateTime= new DateTime) extends AuthenticationBuilder {
  /**
   * Gets the authenticated principal.
   *
   * @return Principal.
   */
  override def getPrincipal: Principal = p

  /**
   * Adds metadata about a credential presented for authentication.
   *
   * @param credential Credential metadata.
   *
   * @return This builder instance.
   */
  override def addCredential(credential: CredentialMetaData): AuthenticationBuilder = this.copy(credentials = credentials.::(credential) )

  /**
   * Adds an authentication metadata attribute key-value pair.
   *
   * @param key Authentication attribute key.
   * @param value Authentication attribute value.
   *
   * @return This builder instance.
   */
  override def addAttribute(key: String, value: Any): AuthenticationBuilder = this.copy(attributes = attributes.+(key-> value))

  /**
   * Sets the authentication metadata attributes.
   *
   * @param attributes Non-null map of authentication metadata attributes.
   *
   * @return This builder instance.
   */
  override def setAttributes(attributes: Map[String, Any]): AuthenticationBuilder = this.copy(attributes = attributes)

  /**
   * Sets the principal returns this instance.
   *
   * @param p Authenticated principal.
   *
   * @return This builder instance.
   */
  override def setPrincipal(p: Principal): AuthenticationBuilder = this.copy(p = p)

  /**
   * Gets the authentication success map.
   *
   * @return Non-null map of handler names to successful handler authentication results.
   */
  override def getSuccesses: Map[String, HandlerResult] = this.successes

  /**
   * Sets the authentication date and returns this instance.
   *
   * @param d Authentication date.
   *
   * @return This builder instance.
   */
  override def setAuthenticationDate(d: Date): AuthenticationBuilder = this.copy(authenticationDate = new DateTime(d.getTime))

  /**
   * Adds an authentication failure to the map of handler names to the authentication handler failures.
   *
   * @param key Authentication handler name.
   * @param value Exception raised on handler failure to authenticate credential.
   *
   * @return This builder instance.
   */
  override def addFailure(key: String, value: Class[_ <: Exception]): AuthenticationBuilder = this.copy(failures = failures.+(key -> value))

  /**
   * Creates an immutable authentication instance from builder data.
   *
   * @return Immutable authentication.
   */
  override def build: Authentication = this.build

  /**
   * Adds an authentication success to the map of handler names to successful authentication handler results.
   *
   * @param key Authentication handler name.
   * @param value Successful authentication handler result produced by handler of given name.
   *
   * @return This builder instance.
   */
  override def addSuccess(key: String, value: HandlerResult): AuthenticationBuilder = this.copy(successes = successes.+(key -> value))

  /**
   * Gets the authentication failure map.
   *
   * @return Non-null authentication failure map.
   */
  override def getFailures: Map[String, Class[_ <: Exception]] = this.failures
}
