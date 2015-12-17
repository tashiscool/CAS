package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait Credentials { def id: String }

case class UsernamePasswordCredential(id:String, userName: String, password: String) extends Credentials