import java.io.File

import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import module.ApplicationModule
import play.api.i18n.Lang
import play.api.mvc.Results._
import play.api._
import play.api.Play._
import play.api.mvc.{RequestHeader, WithFilters}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Created by tash on 12/15/15.
 */
object Global extends play.api.GlobalSettings {

  private lazy val injector = {
      Guice.createInjector(new ApplicationModule)
    }
  
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

}
