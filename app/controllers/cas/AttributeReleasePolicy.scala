package controllers.cas

import org.apache.commons.lang3.builder.{ToStringBuilder, EqualsBuilder, HashCodeBuilder}

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
  def setAttributeFilter(filter: Map[String, AnyRef] => Map[String, AnyRef]):AttributeReleasePolicy

  /**
   * Gets the attributes, having applied the filter.
   *
   * @param p the principal that contains the resolved attributes
   * @return the attributes
   */
  def getAttributes(p: Principal): Map[String, AnyRef]
}

case class ReturnAllowedAttributeReleasePolicy(allowedAttributes: List[String], filter: (Map[String, AnyRef]) => Map[String, AnyRef]) extends AttributeReleasePolicy{

  protected def getAttributesInternal(resolvedAttributes: Map[String, AnyRef]): Map[String, AnyRef] = {
    resolvedAttributes.filter{case(x,y) => allowedAttributes.contains(x)}
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) {
      return false
    }
    if (obj == this) {
      return true
    }
    if (obj.getClass ne getClass) {
      return false
    }
    val rhs: ReturnAllowedAttributeReleasePolicy = obj.asInstanceOf[ReturnAllowedAttributeReleasePolicy]
    return new EqualsBuilder().appendSuper(super.equals(obj)).append(this.allowedAttributes, rhs.allowedAttributes).isEquals
  }

  override def hashCode: Int = {
    return new HashCodeBuilder(13, 133).appendSuper(super.hashCode).append(allowedAttributes).toHashCode
  }

  override def toString: String = {
    return new ToStringBuilder(this).appendSuper(super.toString).append("allowedAttributes", allowedAttributes).toString
  }

  /**
   * Is authorized to release credential password?
   *
   * @return the boolean
   */
  override def isAuthorizedToReleaseCredentialPassword: Boolean = ???

  /**
   * Gets the attributes, having applied the filter.
   *
   * @param p the principal that contains the resolved attributes
   * @return the attributes
   */
  override def getAttributes(p: Principal): Map[String, AnyRef] = ???

  /**
   * Is authorized to release proxy granting ticket?
   *
   * @return the boolean
   */
  override def isAuthorizedToReleaseProxyGrantingTicket: Boolean = ???

  /**
   * Sets the attribute filter.
   *
   * @param filter the new attribute filter
   */
  override def setAttributeFilter(filter: (Map[String, AnyRef]) => Map[String, AnyRef]): ReturnAllowedAttributeReleasePolicy = this.copy(filter = filter )
}

