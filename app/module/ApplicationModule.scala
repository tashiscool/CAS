package module

import controllers.cas._
import models.dao.sapi.{UserCrendentialDaoReactiveImpl, UserDaoReactiveImpl, UserCrendentialDaoReactive}
import net.codingwell.scalaguice.ScalaModule
import utils.scalautils.{CacheServiceImpl, CacheService}

class ApplicationModule extends ScalaModule {
  override def configure(): Unit = {
    bind[CentralAuthenicationService].to[CentralAuthenticationServiceImpl].asEagerSingleton
    bind[AuthenticationManager].to[PolicyBasedAuthenticationManager].asEagerSingleton
    bind[ExpirationPolicy].to[NeverExpiresExpirationPolicy].asEagerSingleton
    bind[LogoutManager].to[LogoutManagerImpl].asEagerSingleton
    val factory = new DefaultPrincipalFactory
    val credentialsReactive = new UserCrendentialDaoReactiveImpl()
    val userReactive = new UserDaoReactiveImpl()
    bind[PrincipalFactory].toInstance(factory)
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

    val principalHandlers: Map[AuthenticationHandler, PrincipalResolver] =
      Map(new QueryDatabaseAuthenticationHandler(principalFactory = factory, credentialsReactive = credentialsReactive)
        -> new PersonDirectoryPrincipalResolver(principalAttributeName = "", userDaoReactive = userReactive))
    val authHandlers = List.empty[AuthenticationHandler].toSeq
    val metadataPopulators = List(new SuccessfulHandlerMetaDataPopulator(null, null))
    val servicesList = List("")

    bind[Seq[AuthenticationHandler]].toInstance(authHandlers)
    bind[List[AuthenticationMetaDataPopulator]].toInstance(metadataPopulators)
    bind[List[String]].toInstance(servicesList)
    bind[Map[AuthenticationHandler,PrincipalResolver]].toInstance(principalHandlers)
  }
}

