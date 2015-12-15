package controllers.cas

/**
 * Created by tash on 11/23/15.
 */
sealed trait LogoutType
case object NONE extends LogoutType  { val name = "NONE" } //etc.
case object BACK_CHANNEL extends LogoutType  { val name = "BACK_CHANNEL" } //etc.
case object FRONT_CHANNEL extends LogoutType  { val name = "FRONT_CHANNEL" } //etc.