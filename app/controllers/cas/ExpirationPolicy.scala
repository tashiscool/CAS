package controllers.cas

import java.util.concurrent.TimeUnit

/**
 * Created by tash on 11/19/15.
 */
trait ExpirationPolicy extends Serializable{
  def isExpired (ticketState: TicketState):Boolean
}

case class HardTimeoutExpirationPolicy(timeToKill: Long, timeUnit: TimeUnit) extends ExpirationPolicy{
  def isExpired(ticketState: TicketState):Boolean = {
    return (ticketState == null) || (System.currentTimeMillis - ticketState.getCreationTime >= this.timeToKill)
  }
}

case class MultiTimeUseOrTimeoutExpirationPolicy(numberOfUses: Int, timeToKill: Long, timeUnit: TimeUnit) extends ExpirationPolicy {
  def isExpired(ticketState: TicketState) = {
    (ticketState == null) || (ticketState.getCountOfUses >= this.numberOfUses) || (System.currentTimeMillis() - ticketState.getLastTimeUsed >= this.timeToKill)
  }
}

class NeverExpiresExpirationPolicy extends ExpirationPolicy{
  def isExpired(ticketState: TicketState):Boolean= {
    false
  }
}

case class RememberMeDelegatingExpirationPolicy(rememberMeExpirationPolicy: ExpirationPolicy,sessionExpirationPolicy: ExpirationPolicy) extends ExpirationPolicy{
  def isExpired(ticketState: TicketState) = {
    val b: Boolean = ticketState.getAuthentication.getAttributes.get(RememberMeCredential.AUTHENTICATION_ATTRIBUTE_REMEMBER_ME).getOrElse(false).asInstanceOf[Boolean]
    if ((!b.booleanValue())) {
      this.sessionExpirationPolicy.isExpired(ticketState)
    }else{
      this.rememberMeExpirationPolicy.isExpired(ticketState)
    }
  }
}


case class ThrottledUseAndTimeoutExpirationPolicy(timeToKillInMilliSeconds: Long, timeInBetweenUsesInMilliSeconds:Long) extends ExpirationPolicy{
  def isExpired(ticketState: TicketState) = {
    val currentTimeInMillis: Long = System.currentTimeMillis
    val lastTimeTicketWasUsed: Long = ticketState.getLastTimeUsed
    if (ticketState.getCountOfUses == 0 && (currentTimeInMillis - lastTimeTicketWasUsed < this.timeToKillInMilliSeconds)) {
      false
    } else if ((currentTimeInMillis - lastTimeTicketWasUsed >= this.timeToKillInMilliSeconds)) {
      true
    } else if ((currentTimeInMillis - lastTimeTicketWasUsed <= this.timeInBetweenUsesInMilliSeconds)) {
      true
    }else false
  }
}

case class TimeoutExpirationPolicy(timeToKillInMilliSeconds: Long) extends ExpirationPolicy {
  def isExpired(ticketState: TicketState):Boolean = {
    return (ticketState == null) || (System.currentTimeMillis - ticketState.getLastTimeUsed >= this.timeToKillInMilliSeconds)
  }
}

object RememberMeCredential{
  /** Authentication attribute name for remember-me. **/
  val AUTHENTICATION_ATTRIBUTE_REMEMBER_ME: String = "org.jasig.cas.authentication.principal.REMEMBER_ME"

  /** Request parameter name. **/
  val REQUEST_PARAMETER_REMEMBER_ME: String = "rememberMe"
}

trait RememberMeCredential extends Credentials {

/**
 * Checks if remember-me is enabled.
 *
 * @return true, if  remember me
 */
def isRememberMe: Boolean

/**
 * Sets the remember me.
 *
 * @param rememberMe the new remember me
 */
def setRememberMe(rememberMe: Boolean): RememberMeCredential
}