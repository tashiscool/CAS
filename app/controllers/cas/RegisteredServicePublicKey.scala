package controllers.cas

import java.security.PublicKey

/**
 * Created by tash on 11/23/15.
 */
trait RegisteredServicePublicKey extends Serializable{
  /**
   * Gets location to the public key file.
   *
   * @return the location
   */
  def getLocation: String

  /**
   * Gets algorithm for the public key.
   *
   * @return the algorithm
   */
  def getAlgorithm: String

  /**
   * Create instance.
   *
   * @return the public key
   * @throws Exception the exception thrown if the public key cannot be created
   */
  @throws(classOf[Exception])
  def createInstance: PublicKey
}

class DefaultRegisteredServicePublicKey extends RegisteredServicePublicKey {
  /**
   * Create instance.
   *
   * @return the public key
   * @throws Exception the exception thrown if the public key cannot be created
   */
  override def createInstance: _root_.java.security.PublicKey = null

  /**
   * Gets algorithm for the public key.
   *
   * @return the algorithm
   */
  override def getAlgorithm: String = "unsupported"

  /**
   * Gets location to the public key file.
   *
   * @return the location
   */
  override def getLocation: String = "none"
}
