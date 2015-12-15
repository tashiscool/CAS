package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
sealed trait LogoutRequestStatus { def name: String }

case object NOT_ATTEMPTED extends LogoutRequestStatus  { val name = "NOT_ATTEMPTED" } //etc.
case object FAILURE extends LogoutRequestStatus  { val name = "FAILURE" } //etc.
case object SUCCESS extends LogoutRequestStatus  { val name = "SUCCESS" } //etc.