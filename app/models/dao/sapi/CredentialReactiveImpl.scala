package models.dao.sapi

import java.util
import java.util.UUID

import com.mongodb.util.{JSONParseException, JSON}
import com.mongodb.{MongoClientOptions, MongoClientURI, MongoClient, DBObject}
import controllers.cas.Credentials
import org.springframework.data.mapping.model.MappingException
import org.springframework.data.mongodb.MongoDbFactory
import org.springframework.data.mongodb.core.{SimpleMongoDbFactory, MongoOperations, MongoTemplate}
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

import scala.collection.JavaConverters._
import scala.concurrent.Future

import play.Logger
import play.api.Play.current
import play.api.libs.json._
import play.modules.reactivemongo.json.collection.JSONCollection

import org.apache.commons.lang3.StringUtils
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.SerializationUtils._
import org.springframework.data.mongodb.core.query.{Criteria, Query}
import plugins.ReactiveMongoPlayPlugin
import reactivemongo.core.commands.LastError
import utils.scalautils.MongoJson
import java.util.HashSet
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import com.mongodb._



/**
 * Created by tash on 9/19/14.
 */

case class UserCrendential(id: String, userId: String, lookupValues: Map[String,String])

trait UserCrendentialDaoReactive {
  def createUserCrendential(userCrendential: UserCrendential): Future[LastError]

  def getUserCrendentialByQ(name: String): Future[Option[UserCrendential]]


}
class UserCrendentialDaoReactiveImpl extends UserCrendentialDaoReactive{
  val conf = Play.configuration
  val DB_NAME = "db"
  val UserCrendentialCollectionNameString: String = "UserCrendentials"
  object UserCrendentialDao {
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
  def collection = db.collection[JSONCollection](UserCrendentialCollectionNameString)

  def getSurveyByQueryString(queryString: Map[String, Seq[String]]): Future[List[UserCrendential]] = {
    val query: Query = new Query
    queryString.map {case (k,v) =>
      val criteria: Criteria = Criteria.where(k).in(v)
      query.addCriteria(criteria)
    }
    collection.find(Json.parse(serializeToJsonSafely(query.getQueryObject))).cursor[JsValue].collect[List]().map {
      result =>
        result.map(Survey => UserCrendentialDao.fromInstanceDBObject(MongoJson.fromJson(Survey), classOf[UserCrendential]))
    } recover {
      case e: Exception =>
        Logger.error(s"Error fetching Survey with name $query from mongo", e)
        List.empty[UserCrendential]
    }
  }

  override def createUserCrendential(userCrendential: UserCrendential): Future[LastError] = ???

  override def getUserCrendentialByQ(name: String): Future[Option[UserCrendential]] = ???
}