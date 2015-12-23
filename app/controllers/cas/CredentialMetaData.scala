package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait CredentialMetaData extends Serializable{
  def getId: String
}

case class BasicCredentialMetaData(credential: Credentials) extends CredentialMetaData {
  override def getId: String = credential.id
}
class PrincipalBearingCredential extends CredentialMetaData {
  override def getId: String = ""
}