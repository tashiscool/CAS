package controllers.cas



import com.mongodb.util.{JSONParseException, JSON}
import com.mongodb.{MongoClientURI, MongoClient, DBObject}
import org.springframework.data.mapping.model.MappingException
import org.springframework.data.mongodb.MongoDbFactory
import org.springframework.data.mongodb.core.{SimpleMongoDbFactory, MongoOperations, MongoTemplate}
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

import scala.concurrent.Future

import play.Logger
import play.api.Play.current
import play.api.libs.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import org.springframework.data.mongodb.core.query.SerializationUtils._
import org.springframework.data.mongodb.core.query.{Criteria, Query}
import plugins.ReactiveMongoPlayPlugin
import utils.scalautils.MongoJson
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import com.mongodb._

/**
 * Created by tash on 12/15/15.
 */
trait ServiceRegistryDao {
/**
 * Persist the service in the data store.
 *
 * @param registeredService the service to persist.
 * @return the updated RegisteredService.
 */
def save (registeredService: RegisteredService): Future[RegisteredService]

/**
 * Remove the service from the data store.
 *
 * @param registeredService the service to remove.
 * @return true if it was removed, false otherwise.
 */
def delete (registeredService: RegisteredService): Future[Boolean]

/**
 * Retrieve the services from the data store.
 *
 * @return the collection of services.
 */
def load: Future[List[RegisteredService]]

/**
 * Find service by the numeric id.
 *
 * @param id the id
 * @return the registered service
 */
def findServiceById(id: Long): Future[RegisteredService]
}
class RegisteredServiceDaoReactiveImpl extends ServiceRegistryDao {
  val conf = Play.configuration
  val DB_NAME = "db"
  val RegisteredServiceCollectionNameString: String = "RegisteredServices"
  object RegisteredServiceDao {
    protected var mongoOperations: MongoOperations = null
    protected var converter: MappingMongoConverter = null
    val otherUri = System.getProperties().getProperty("mongodb.uri")
    val URL = if(otherUri != null) otherUri else conf.getString("mongodb.db").getOrElse(throw conf.globalError("Missing configuration key 'mongodb.db'!"))
    val mongo = new MongoClient(new MongoClientURI(URL))
    val dbFactory: MongoDbFactory = new SimpleMongoDbFactory(new MongoURI(URL))

    def fromInstanceDBObject[T](dbo: DBObject, entityClass: Class[T]): T = {
      val context: MongoMappingContext = new MongoMappingContext
      converter = new MappingMongoConverter(dbFactory, context)
      converter.setMapKeyDotReplacement("-")
      mongoOperations = new MongoTemplate(dbFactory, converter)
      val source: T = converter.read(entityClass, dbo)
      return source
    }
    def getInstanceDBObject[T](objectToSave: T): DBObject = {
      val context: MongoMappingContext = new MongoMappingContext
      converter = new MappingMongoConverter(dbFactory, context)
      converter.setMapKeyDotReplacement("-")
      mongoOperations = new MongoTemplate(dbFactory, converter)
      if (!(objectToSave.isInstanceOf[String])) {
        val dbDoc: DBObject = new BasicDBObject
        converter.write(objectToSave, dbDoc)
        return dbDoc
      }
      else {
        try {
          return JSON.parse(objectToSave.asInstanceOf[String]).asInstanceOf[DBObject]
        }
        catch {
          case e: JSONParseException => {
            throw new MappingException("Could not parse given String to save into a JSON document!", e)
          }
        }
      }
    }
  }
  def driver = ReactiveMongoPlayPlugin.driver
  /** Returns the current MongoConnection instance (the connection pool manager). */
  def connection = ReactiveMongoPlayPlugin.connection
  /** Returns the default database (as specified in `application.conf`). */
  def db = ReactiveMongoPlayPlugin.db
  def collection = db.collection[JSONCollection](RegisteredServiceCollectionNameString)

  def getRegsiteredServicesByQueryString(queryString: Map[String, Seq[String]]): Future[List[RegisteredService]] = {
    val query: Query = new Query
    queryString.map {case (k,v) =>
      val criteria: Criteria = Criteria.where(k).in(v)
      query.addCriteria(criteria)
    }
    collection.find(Json.parse(serializeToJsonSafely(query.getQueryObject))).cursor[JsValue].collect[List]().map {
      result =>
        result.map(regsiteredServices => RegisteredServiceDao.fromInstanceDBObject(MongoJson.fromJson(regsiteredServices), classOf[RegisteredService]))
    } recover {
      case e: Exception =>
        Logger.error(s"Error fetching regsiteredServices with name $query from mongo", e)
        List.empty[RegisteredService]
    }
  }

  /**
   * Persist the service in the data store.
   *
   * @param registeredService the service to persist.
   * @return the updated RegisteredService.
   */
  override def save(registeredService: RegisteredService): Future[RegisteredService] = {
    collection.save(MongoJson.toJson(RegisteredServiceDao.getInstanceDBObject(registeredService))).map(_ => registeredService)
  }

  /**
   * Remove the service from the data store.
   *
   * @param registeredService the service to remove.
   * @return true if it was removed, false otherwise.
   */
  override def delete(registeredService: RegisteredService): Future[Boolean] = {
    val query: Query = new Query().addCriteria(Criteria.where("_id").is(registeredService.getId))
    collection.remove(Json.parse(serializeToJsonSafely(query.getQueryObject))).map(_.ok)
  }

  /**
   * Retrieve the services from the data store.
   *
   * @return the collection of services.
   */
  override def load: Future[List[RegisteredService]] = collection.find(Json.parse(serializeToJsonSafely(new Query().getQueryObject))).cursor[JsValue].collect[List]().map {
      result =>
        result.map(regsiteredServices => RegisteredServiceDao.fromInstanceDBObject(MongoJson.fromJson(regsiteredServices), classOf[RegisteredService]))
    } recover {
    case e: Exception =>
      Logger.error(s"Error fetching regsiteredServices with name from mongo", e)
      List.empty[RegisteredService]
    }

  /**
   * Find service by the numeric id.
   *
   * @param id the id
   * @return the registered service
   */
  override def findServiceById(id: Long): Future[RegisteredService] = {
    val query: Query = new Query().addCriteria(Criteria.where("_id").is(id))
    collection.find(Json.parse(serializeToJsonSafely(query.getQueryObject))).cursor[JsValue].collect[List]().map {
      result =>
        val stage = result.map(regsiteredServices => RegisteredServiceDao.fromInstanceDBObject(MongoJson.fromJson(regsiteredServices), classOf[RegisteredService]))
        stage.headOption.getOrElse(null)
    } recover {
      case e: Exception =>
        Logger.error(s"Error fetching regsiteredServices with name from mongo", e)
        null
    }
  }
}