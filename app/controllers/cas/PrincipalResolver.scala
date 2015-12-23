package controllers.cas

import models.dao.sapi.{ValueHelper, User, UserDaoReactive}
import org.slf4j.{LoggerFactory, Logger}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Created by tash on 11/24/15.
 */
trait PrincipalResolver {
/**
 * Resolves a principal from the given credential using an arbitrary strategy.
 *
 * @param credential Source credential.
 *
 * @return Resolved principal, or null if the principal could not be resolved.
 */
def resolve (credential: Credentials): Future[Principal]

/**
 * Determines whether this instance supports principal resolution from the given credential. This method SHOULD
 * be called prior to {@link #resolve(org.jasig.cas.authentication.Credential)}.
 *
 * @param credential The credential to check for support.
 *
 * @return True if credential is supported, false otherwise.
 */
def supports (credential: Credentials): Boolean
}

case class BasicPrincipalResolver(principalFactory: PrincipalFactory ) extends PrincipalResolver {

  def resolve (credential: Credentials): Future[Principal] = {
    Future.successful(this.principalFactory.createPrincipal (credential.id))
  }

  def supports (credential: Credentials): Boolean = {
    return credential.id != null
  }

  /**
   * Sets principal factory to create principal objects.
   *
   * @param principalFactory the principal factory
   */
  def setPrincipalFactory (principalFactory: PrincipalFactory):BasicPrincipalResolver = {
    this.copy(principalFactory = principalFactory)
  }
}


class ChainingPrincipalResolver(chain: List[PrincipalResolver]) extends PrincipalResolver {
  /**
   * Resolves a principal from the given credential using an arbitrary strategy.
   *
   * @param credential Source credential.
   *
   * @return Resolved principal, or null if the principal could not be resolved.
   */
  override def resolve(credential: Credentials): Future[Principal] = {
    chain.foldLeft(List.empty[Future[Principal]]){case (resultList, resolver) =>
      val resultF = resultList.lastOption.getOrElse(null)
      val inputF = resultF.map{result =>
        val input = if(result !=null){
          new IdentifiableCredential(result.getId)
        }
        else {
          credential
        }
        input
      }
      val foo = inputF.flatMap{input =>
        resolver.resolve(input)
      }
      resultList.::(foo)
    }.lastOption.getOrElse(null)
  }

  /**
   * Determines whether this instance supports principal resolution from the given credential. This method SHOULD
   * be called prior to {@link #resolve(org.jasig.cas.authentication.Credential)}.
   *
   * @param credential The credential to check for support.
   *
   * @return True if credential is supported, false otherwise.
   */
  override def supports(credential: Credentials): Boolean = {
    chain.headOption.getOrElse(throw new RuntimeException("ChainingPrincipalResolver unusable configuration")).supports(credential)
  }

  class IdentifiableCredential(idCredntia: String) extends Credentials {
    override def id: String = idCredntia
  }
}

case class PersonDirectoryPrincipalResolver(returnNullIfNoAttributes: Boolean = false,
                                            principalFactory: PrincipalFactory = new DefaultPrincipalFactory,
                                            principalAttributeName: String, userDaoReactive: UserDaoReactive ) extends PrincipalResolver {
  val logger: Logger  = play.Logger.underlying()

  /**
   * Resolves a principal from the given credential using an arbitrary strategy.
   *
   * @param credential Source credential.
   *
   * @return Resolved principal, or null if the principal could not be resolved.
   */
  override def resolve(credential: Credentials): Future[Principal] = {
    logger.debug("Attempting to resolve a principal...")
    var principalId: String = credential.id
    if (principalId == null) {
      logger.debug("Got null for extracted principal ID; returning null.")
      return null
    }
    logger.debug(s"Creating SimplePrincipal for [${principalId}]")
    val personAttributesF: Future[Option[User]] = userDaoReactive.getUserById(principalId)
    personAttributesF.map{ case Some(personAttributes) if (personAttributes != null) =>
      val attributes = personAttributes.attributes
      if (attributes == null || attributes.isEmpty) {
        if (!this.returnNullIfNoAttributes) {
          this.principalFactory.createPrincipal(principalId)
        }else{
          null
        }
      }else{
        val mappedValues: Map[String, String] = Map()
        val convertedAttributes = attributes.foldLeft(mappedValues){case(mappedValues, (key, value)) =>
         val values =  ValueHelper.flattenValue(value)
          if (key.equalsIgnoreCase(this.principalAttributeName)) {
            if (values.isEmpty) {
              logger.debug(s"${this.principalAttributeName} is empty, using ${principalId} for principal")
              mappedValues
            }
            else {
              principalId = values.headOption.getOrElse("")
              logger.debug(s"Found principal attribute value ${principalId}; removing ${this.principalAttributeName} from attribute map." )
              mappedValues
            }
          }
          else {
            val forSave = if (values.size == 1) values.headOption.getOrElse("") else values.mkString(".")
            mappedValues.+(key -> forSave)
          }
        }
        this.principalFactory.createPrincipal(principalId, convertedAttributes)
      }
    case x => logger.error(s"huh $x"); null
    }
  }

  /**
   * Determines whether this instance supports principal resolution from the given credential. This method SHOULD
   * be called prior to {@link #resolve(org.jasig.cas.authentication.Credential)}.
   *
   * @param credential The credential to check for support.
   *
   * @return True if credential is supported, false otherwise.
   */
  override def supports(credential: Credentials): Boolean = true
}