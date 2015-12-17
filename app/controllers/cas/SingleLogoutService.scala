package controllers.cas

/**
 * Created by tash on 11/19/15.
 */
trait SingleLogoutService{
  /**
   * Return if the service is already logged out.
   *
   * @return if the service is already logged out.
   */
  def isLoggedOutAlready: Boolean

  /**
   * Set if the service is already logged out.
   *
   * @param loggedOutAlready if the service is already logged out.
   */
  def setLoggedOutAlready(loggedOutAlready: Boolean):SingleLogoutService
}

case class SingleLogoutServiceImpl(loggedOut:Boolean)extends SingleLogoutService {
  /**
   * Return if the service is already logged out.
   *
   * @return if the service is already logged out.
   */
  override def isLoggedOutAlready: Boolean = loggedOut

  /**
   * Set if the service is already logged out.
   *
   * @param loggedOutAlready if the service is already logged out.
   */
  override def setLoggedOutAlready(loggedOutAlready: Boolean): SingleLogoutService = this.copy(loggedOut = loggedOutAlready)
}