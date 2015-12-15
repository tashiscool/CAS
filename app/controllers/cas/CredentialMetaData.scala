package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait CredentialMetaData{
  def getId: String
}

@SerialVersionUID(4929579849241505377L)
case class BasicCredentialMetaData(credential: Credentials) extends CredentialMetaData {
  /** Serialization version marker. */
  private val serialVersionUID: Long = 4929579849241505377L

  override def getId: String = credential.id
}
