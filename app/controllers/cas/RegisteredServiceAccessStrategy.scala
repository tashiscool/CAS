package controllers.cas

import java.util

import org.slf4j.LoggerFactory

/**
 * Created by tash on 11/23/15.
 */
trait RegisteredServiceAccessStrategy{
  /**
   * Verify is the service is enabled and recognized by CAS.
   *
   * @return true/false if service is enabled
   */
  def isServiceAccessAllowed: Boolean

  /**
   * Assert that the service can participate in sso.
   *
   * @return true/false if service can participate in sso
   */
  def isServiceAccessAllowedForSso: Boolean

  /**
   * Verify authorization policy by checking the pre-configured rules
   * that may depend on what the principal might be carrying.
   *
   * @param principalAttributes the principal attributes. Rather than passing the principal
   *                            directly, we are only allowing principal attributes
   *                            given they may be coming from a source external to the principal
   *                            itself. (Cached principal attributes, etc)
   * @return true/false if service access can be granted to principal
   */
  def doPrincipalAttributesAllowServiceAccess(principalAttributes: Map[String, AnyRef]): Boolean
}
case class DefaultRegisteredServiceAccessStrategy(requiredAttributes:Map[String, Set[String]]) extends RegisteredServiceAccessStrategy with Serializable {

  val requireAllAttributes = false

  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Verify is the service is enabled and recognized by CAS.
   *
   * @return true/false if service is enabled
   */
  override def isServiceAccessAllowed: Boolean = true

  /**
   * Assert that the service can participate in sso.
   *
   * @return true/false if service can participate in sso
   */
  override def isServiceAccessAllowedForSso: Boolean = true

  /**
   * Verify authorization policy by checking the pre-configured rules
   * that may depend on what the principal might be carrying.
   *
   * @param principalAttributes the principal attributes. Rather than passing the principal
   *                            directly, we are only allowing principal attributes
   *                            given they may be coming from a source external to the principal
   *                            itself. (Cached principal attributes, etc)
   * @return true/false if service access can be granted to principal
   */
  override def doPrincipalAttributesAllowServiceAccess(principalAttributes: Map[String, AnyRef]): Boolean = {
      if (this.requiredAttributes.isEmpty) {
        logger.debug(s"No required attributes are specified")
        return true
      }
      if (principalAttributes.isEmpty) {
        logger.debug(s"No principal attributes are found to satisfy attribute requirements")
        return false
      }
      if (principalAttributes.size < this.requiredAttributes.size) {
        logger.debug(s"The size of the principal attributes that are [${ principalAttributes}] does not match requirements, " + "which means the principal is not carrying enough data to grant authorization")
        return false
      }
      val requiredAttrs: Map[String, Set[String]] = requiredAttributes
      logger.debug(s"These required attributes [${requiredAttrs}] are examined against [${principalAttributes}] before service can proceed.")
      val difference  = requiredAttrs.keySet.&(principalAttributes.keySet)
      val copy: Set[String] = difference
      if (this.requireAllAttributes && copy.size < this.requiredAttributes.size) {
        logger.debug(s"Not all required attributes are available to the principal")
        return false
      }
      import scala.collection.JavaConversions._
      for (key <- copy) {
        val requiredValues = this.requiredAttributes.get(key).getOrElse(Set())
        val objVal: Any = principalAttributes.get(key).getOrElse("")
        val availableValues: Set[Any] = objVal match {
          case obj:Iterable[Any] =>obj.toSet
          case _ => Set(objVal)
        }


        val differenceInValues=requiredValues.&(availableValues.map(_.toString) )
        if (!differenceInValues.isEmpty) {
          logger.info("Principal is authorized to access the service")
          return true
        }
      }
      logger.info("Principal is denied access as the required attributes for the registered service are missing")
      return false
  }
}