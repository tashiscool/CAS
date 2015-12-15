package controllers.cas

import java.net.{URLDecoder, URL}

import org.slf4j.LoggerFactory

/**
 * Created by tash on 11/19/15.
 */
trait Service{

  val logger = LoggerFactory.getLogger(this.getClass)

  def getId: String
  /**
   * Sets the principal.
   *
   * @param principal the new principal
   */
  def setPrincipal(principal: Principal):Service

  /**
   * Whether the services matches another.
   *
   * @param service the service
   * @return true, if successful
   */
  def matches(service: Service): Boolean = {
    try {
      val thisUrl: String = URLDecoder.decode(this.getId, "UTF-8")
      val serviceUrl: String = URLDecoder.decode(service.getId, "UTF-8")
      logger.trace(s"Decoded urls and comparing [${thisUrl}] with [${serviceUrl}]")
      return thisUrl.equalsIgnoreCase(serviceUrl)
    }
    catch {
      case e: Exception => {
        logger.error(e.getMessage, e)
      }
    }
    return false
  }
}

sealed trait Responsed { def ResponseType: String }

case object POST extends Responsed  { val ResponseType = "POST" } //etc.
case object REDIRECT extends Responsed  { val ResponseType = "REDIRECT" } //etc

case class SimpleWebApplicationServiceImpl(id: String, originalUrl: String, artifactId: String, responseType: Response, principal: Option[Principal]) extends Service {
  override def getId: String = id

  /**
   * Sets the principal.
   *
   * @param principal the new principal
   */
  override def setPrincipal(principal: Principal): Service = this.copy(principal = Some(principal))
}
case class Response(attributes:Map[String,String], responseType:Responsed)