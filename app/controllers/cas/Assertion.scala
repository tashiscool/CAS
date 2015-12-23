package controllers.cas

import org.apache.commons.lang3.builder.HashCodeBuilder

/**
 * Created by tash on 11/19/15.
 */
trait Assertion extends Serializable{
  /**
   * Gets the authentication event that is basis of this assertion.
   *
   * @return Non-null primary authentication event.
   */
  def getPrimaryAuthentication: Authentication

  /**
   * Gets a list of all authentications that have occurred during a CAS SSO session.
   *
   * @return Non-null, non-empty list of authentications in leaf-first order (i.e. authentications on the root ticket
   *         occur at the end).
   */
  def getChainedAuthentications: List[Authentication]

  /**
   * True if the validated ticket was granted in the same transaction as that
   * in which its grantor GrantingTicket was originally issued.
   *
   * @return true if validated ticket was granted simultaneous with its
   *         grantor's issuance
   */
  def isFromNewLogin: Boolean

  /**
   * Method to obtain the service for which we are asserting this ticket is
   * valid for.
   *
   * @return the service for which we are asserting this ticket is valid for.
   */
  def getService: Service
}

case class ImmutableAssertion(primary: Authentication,
                              chained: List[Authentication],
                              service: Service,
                              fromNewLogin: Boolean) extends Assertion {
  override def getPrimaryAuthentication: Authentication = primary

  override def isFromNewLogin: Boolean = fromNewLogin

  override def getChainedAuthentications: List[Authentication] = chained

  override def getService: Service = service

  override def equals(o: Any): Boolean = {
    if (!(o.isInstanceOf[Assertion])) {
      return false
    }
    val a: Assertion = o.asInstanceOf[Assertion]
    return (this.getPrimaryAuthentication == a.getPrimaryAuthentication) && (this.getChainedAuthentications == a.getChainedAuthentications) && (this.service == a.getService) && this.fromNewLogin == a.isFromNewLogin
  }

  override def hashCode: Int = {
    val builder: HashCodeBuilder = new HashCodeBuilder(15, 11)
    builder.append(this.getPrimaryAuthentication)
    builder.append(this.getChainedAuthentications)
    builder.append(this.service)
    builder.append(this.fromNewLogin)
    return builder.toHashCode
  }

  override def toString: String = {
    return this.getPrimaryAuthentication.toString + ':' + this.service.toString
  }
}