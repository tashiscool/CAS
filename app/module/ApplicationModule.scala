package module

import controllers.cas._
import net.codingwell.scalaguice.ScalaModule
import utils.scalautils.{CacheServiceImpl, CacheService}

class ApplicationModule extends ScalaModule {
  override def configure(): Unit = {
    bind[CentralAuthenicationService].to[CentralAuthenticationServiceImpl].asEagerSingleton
    bind[AuthenticationManager].to[PolicyBasedAuthenticationManager].asEagerSingleton
    bind[ExpirationPolicy].to[NeverExpiresExpirationPolicy].asEagerSingleton
    bind[LogoutManager].to[LogoutManagerImpl].asEagerSingleton
    bind[PrincipalFactory].to[DefaultPrincipalFactory].asEagerSingleton
    bind[ServicesManager].to[DefaultServicesManagerImpl].asEagerSingleton
    bind[TicketRegistry].to[MemcacheTicketRegistry].asEagerSingleton
    bind[UniqueTicketIdGenerator].to[DefaultUniqueTicketIdGenerator].asEagerSingleton
    bind[CacheService].to[CacheServiceImpl].asEagerSingleton
    bind[ServicesManager].to[DefaultServicesManagerImpl].asEagerSingleton
    bind[UniqueTicketIdGenerator].to[DefaultUniqueTicketIdGenerator].asEagerSingleton
    val generator = DefaultLongNumericGenerator(0)
    bind[NumericGenerator].toInstance(generator)
    val randomString = new DefaultRandomStringGenerator(35)
    bind[RandomStringGenerator].toInstance(randomString)
    bind[ServiceRegistryDao].to[RegisteredServiceDaoReactiveImpl].asEagerSingleton

    val principalHandlers = Map.empty[AuthenticationHandler,PrincipalResolver]
    val authHandlers = List.empty[AuthenticationHandler].toSeq
    val metadataPopulators = List.empty[AuthenticationMetaDataPopulator]
    val servicesList = List("")

    bind[Seq[AuthenticationHandler]].toInstance(authHandlers)
    bind[List[AuthenticationMetaDataPopulator]].toInstance(metadataPopulators)
    bind[List[String]].toInstance(servicesList)
    bind[Map[AuthenticationHandler,PrincipalResolver]].toInstance(principalHandlers)
  }
}

