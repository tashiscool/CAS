package models.dao.sapi

import java.text.SimpleDateFormat
import java.util
import java.util.{Date}

import com.mongodb.util.{JSONParseException, JSON}
import com.mongodb.{MongoClientURI, MongoClient, DBObject}
import org.joda.time.format.ISODateTimeFormat
import org.springframework.data.mapping.model.MappingException
import org.springframework.data.mongodb.MongoDbFactory
import org.springframework.data.mongodb.core.{SimpleMongoDbFactory, MongoOperations, MongoTemplate}
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

import scala.collection.JavaConversions
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
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import com.mongodb._



/**
 * Created by tash on 9/19/14.
 */
trait Value {
  import scala.Predef._
  override def toString = {
    this match {
      case v: SimpleObject => "\""+v.value +"\""
      case v: ListObject =>val str = s"[${v.values.map(k => s"$k,").mkString(" ")}]"
        val ind = str.lastIndexOf(",")
        scala.collection.mutable.StringBuilder.newBuilder.append(str).replace(ind, ind + 1,"").toString()
      case v: ComplexObject =>
        val str = s"{ ${v.attribute.map{ case (k,v) => s""" "${k}":${v.toString},"""  }.mkString(" ") } }"
        val ind = str.lastIndexOf(",")
        scala.collection.mutable.StringBuilder.newBuilder.append(str).replace(ind, ind + 1,"").toString()
      case _ => ""
    }
  }
}

case class ComplexObject(attribute: Map[String, Value]) extends Value
case class SimpleObject(value: String) extends Value
case class ListObject(values: Seq[Value]) extends Value

case class User(id: String, attributes: Map[String, Value])

object ValueHelper{
  def flattenValue(value: Value): List[String] = {
    flatten(value)
  }

  def flatten(c: ListObject): List[String] = c.values.map(flatten(_)).flatten.toList

  def flatten(c: ComplexObject): List[String] = c.attribute.values.map(v => flatten(v)).flatten.toList

  def flatten(value: Value):List[String] = {
    value match {
      case c:ComplexObject => flatten(c)
      case c:ListObject => flatten(c)
      case c:SimpleObject => List(c.value)
      case _ => List("")
    }
  }

  def parse(v: JsValue) :Value = v match {
    case v: JsObject => parse(v)
    case v: JsArray => parse(v)
    case v: JsString => SimpleObject(v.value)
    case v: JsNumber => SimpleObject(v.value.toString())
    case v: JsBoolean => SimpleObject(v.value.toString())
    case JsNull => SimpleObject(null)
    case v: JsUndefined => SimpleObject(null)
  }
  private def parse(map: JsObject) :ComplexObject = ComplexObject(
    Map() ++ map.fields.map { p =>
      val key = p._1
      val value = p._2 match {
        case v: JsObject =>
          specialMongoJson(v).fold (
            normal => parse(normal),
            special => SimpleObject(special.toString)
          )
        case v: JsArray => { parse(v) }
        case v: JsValue => { parse(v) }
      }
      (key, value)
    })

  val formater = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  private def specialMongoJson(json: JsObject) :Either[JsObject, Object] = {
    if(json.fields.length > 0) {
      json.fields(0) match {
        case (k, v :JsString) if k == "$date" => Right(
          try{
            if(v.value.toLowerCase.contains("t")){
              formater.parse(v.value)
            }else{
              new Date(v.value.toLong)
            }
          }catch{
            case e: Exception => ISODateTimeFormat.dateTime().parseDateTime(v.value).toDate()
          }
        )
        case (k, v :JsNumber) if k == "$date" => Right(new Date(v.value.toLong))
        case (k, v :JsString) if k == "$oid" => Right(new ObjectId( v.value ))
        case (k, v) if k.startsWith("$") => throw new RuntimeException("unsupported specialMongoJson " + k + " with v: " + v.getClass + ":" + v.toString())
        case _ => Left(json)
      }
    } else Left(json)

  }

  private def parse(array: JsArray) :ListObject = {
    val r = (scala.collection.JavaConversions.seqAsJavaList(array.value map { v =>
      parse(v)
    }))
    ListObject(r.asScala.toSeq)
  }
}



trait UserDaoReactive {
  def getUserById(principalId: String): Future[Option[User]]

  def createUser(user: User): Future[LastError]

  def getUserByQ(name: String): Future[Option[User]]


}

class UserDaoReactiveImpl extends UserDaoReactive{
  val conf = Play.configuration
  val DB_NAME = "db"
  val userCollectionNameString: String = "UserAttributes"
  implicit val userReads2 = new Reads[Map[String, Value]] {
    override def reads(json: JsValue): JsResult[Map[String, Value]] = {
      JsSuccess(json.asInstanceOf[JsObject].value.map{case(k,v) => (k,ValueHelper.parse(v))}).asInstanceOf[JsResult[Map[String, Value]]]
    }
  }
  implicit val userWrites2 = new Writes[Map[String, Value]] {
    override def writes(o: Map[String, Value]): JsValue = {
      val foo = o.map{ case (k,v) => s""" "${k}":${v.toString},"""  }.toList
      val str = s"{ ${foo.mkString(" ") } }"
      val ind = str.lastIndexOf(",")
      val forParse = scala.collection.mutable.StringBuilder.newBuilder.append(str).replace(ind, ind + 1,"").toString()
      Json.parse(forParse)
    }
  }
  implicit val userReads3 = new Reads[Value] {
    override def reads(json: JsValue): JsResult[Value] = JsSuccess(ValueHelper.parse(json))
  }
  implicit val userWrites3 = new Writes[Value] {
    override def writes(o: Value): JsValue = {
      Json.parse(o.toString)
    }
  }
  implicit val userReads4 = Json.writes[ListObject]
  implicit val userWrites4 = Json.reads[ListObject]
  implicit val userReads5 = Json.writes[ComplexObject]
  implicit val userWrites5 = Json.reads[ComplexObject]
  implicit val userReads6 = Json.writes[SimpleObject]
  implicit val userWrites6 = Json.reads[SimpleObject]
  implicit val userReads = Json.writes[User]
  implicit val userWrites = Json.reads[User]


  object userDao {
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
  def collection = db.collection[JSONCollection](userCollectionNameString)

  def getSurveyByQueryString(queryString: Map[String, Seq[String]]): Future[List[User]] = {
    val query: Query = new Query
    queryString.map {case (k,v) =>
      val criteria: Criteria = Criteria.where(k).in(v)
      query.addCriteria(criteria)
    }
    collection.find(Json.parse(serializeToJsonSafely(query.getQueryObject))).cursor[JsValue].collect[List]().map {
      result =>
        result.map(Survey => userDao.fromInstanceDBObject(MongoJson.fromJson(Survey), classOf[User]))
    } recover {
      case e: Exception =>
        Logger.error(s"Error fetching Survey with name $query from mongo", e)
        List.empty[User]
    }
  }

  val defaultUser = User("foobar", Map("qx" -> ComplexObject(Map("bax" -> ListObject(List(SimpleObject("foo"),SimpleObject("bar"))  )  )) ))

  override def getUserById(principalId: String): Future[Option[User]] = {
    Future(Some(defaultUser))
  }

  override def createUser(user: User): Future[LastError] = collection.save(user)

  override def getUserByQ(name: String): Future[Option[User]] = ???
}