package controllers.cas

import org.apache.commons.lang3.builder.{EqualsBuilder, HashCodeBuilder}

/**
 * Created by tash on 11/19/15.
 */
trait Principal extends Serializable{
  /**
   * @return the unique id for the Principal
   */
  def getId: String

  /**
   *
   * @return the map of configured attributes for this principal
   */
  def getAttributes: Map[String, AnyRef]
}

case class NullPrincipal( attributes: Map[String, AnyRef]) extends Principal {

  private val serialVersionUID: Long = 2309300426720915104L

  /** The nobody principal. */
  private val NOBODY: String = "nobody"

  /**
   * @return the unique id for the Principal
   */
  override def getId: String = NOBODY

  /**
   *
   * @return the map of configured attributes for this principal
   */
  override def getAttributes: Map[String, AnyRef] = attributes
}

object NullPrincipal{
  def apply() = {
    new NullPrincipal(Map.empty)
  }
}

case class SimplePrincipal(id:String, attributes: Map[String, AnyRef]) extends Principal {
  /**
   * @return An immutable map of principal attributes
   */
  def getAttributes: Map[String, AnyRef] = attributes

  override def toString: String =  this.id

  override def hashCode: Int = {
    val builder: HashCodeBuilder = new HashCodeBuilder (83, 31)
    builder.append (this.id)
    builder.toHashCode
  }

  def getId: String = this.id

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
    val rhs: SimplePrincipal = obj.asInstanceOf[SimplePrincipal]
    new EqualsBuilder().append(this.id, rhs.id).isEquals
  }
}