package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait MessageDescriptor{
  def getCode: String

  /**
   * Gets default message.
   *
   * @return the default message
   */
  def getDefaultMessage: String

  /**
   * Get params.
   *
   * @return the serializable [ ]
   */
  def getParams: List[Serializable]
}
