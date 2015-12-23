package controllers.cas

/**
 * Created by tash on 11/24/15.
 */
trait AuthenticationPolicy  extends Serializable {
  /**
   * Determines whether an authentication event isSatisfiedBy arbitrary security policy.
   *
   * @param authentication Authentication event to examine for compliance with security policy.
   *
   * @return True if authentication isSatisfiedBy security policy, false otherwise.
   */
  def isSatisfiedBy(authentication: Authentication): Boolean
}
/**
 * A factory for producing (stateful) authentication policies based on arbitrary context data.
 * This component provides a way to inject stateless factories into components that produce stateful
 * authentication policies that can leverage arbitrary contextual information to evaluate security policy.
 *
 * @author Marvin S. Addison
 * @since 4.0.0
 */
trait ContextualAuthenticationPolicyFactory[T]  extends Serializable {
  /**
   * Creates a contextual (presumably stateful) authentication policy based on provided context data.
   *
   * @param context Context data used to create an authentication policy.
   *
   * @return Contextual authentication policy object. The returned object should be assumed to be stateful
   *         and not thread safe unless explicitly noted otherwise.
   */
  def createPolicy(context: T): ContextualAuthenticationPolicy[T]
}

/**
 * A stateful authentication policy that is applied using arbitrary contextual information.
 *
 * @author Marvin S. Addison
 * @since 4.0.0
 */
trait ContextualAuthenticationPolicy[T] extends AuthenticationPolicy {
  /**
   * Gets the context used to evaluate the authentication policy.
   *
   * @return Context information.
   */
  def getContext: T
}



class AcceptAnyAuthenticationPolicyFactory extends ContextualAuthenticationPolicyFactory[ServiceContext] {
  /**
   * Creates a contextual (presumably stateful) authentication policy based on provided context data.
   *
   * @param context Context data used to create an authentication policy.
   *
   * @return Contextual authentication policy object. The returned object should be assumed to be stateful
   *         and not thread safe unless explicitly noted otherwise.
   */
  override def createPolicy(context: ServiceContext): ContextualAuthenticationPolicy[ServiceContext] = new DefaultContextualAuthenticationPolicy(context)
}

class DefaultContextualAuthenticationPolicy(serviceContext: ServiceContext) extends ContextualAuthenticationPolicy[ServiceContext]() {
  def getContext: ServiceContext = {
    serviceContext
  }

  def isSatisfiedBy(authentication: Authentication): Boolean = {
    true
  }
}