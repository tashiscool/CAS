package controllers.cas

import models.dao.sapi.User

/**
 * Created by tash on 11/19/15.
 */
trait Credentials { def id: String }

case class UsernamePasswordCredential(id:String, userName: String, password: String) extends Credentials {
  def setUsername(transformedUsername: String): UsernamePasswordCredential = this.copy(userName = transformedUsername)
}

case class ClientCredential(theCredentials: Credentials, user : User) extends Credentials {
  def getUser = user
  def setUser(user: User):ClientCredential = this.copy(user = user)
  override def id: String = if(user != null) user.id else null
}