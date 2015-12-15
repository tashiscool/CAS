package module

import controllers.cas.{CentralAuthenticationServiceImpl, CentralAuthenicationService}
import net.codingwell.scalaguice.ScalaModule

class ApplicationModule extends ScalaModule {
  override def configure(): Unit = {
    bind[CentralAuthenicationService].to[CentralAuthenticationServiceImpl].asEagerSingleton
  }
}

