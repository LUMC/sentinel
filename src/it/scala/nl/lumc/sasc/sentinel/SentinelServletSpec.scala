package nl.lumc.sasc.sentinel

import java.io.File

import scala.util.Try

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.ClientResponse
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.data.Sized
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.{ BeforeAfter, Specification }
import org.specs2.specification.{ Fragments, Step }

import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ ApiMessage, User }
import nl.lumc.sasc.sentinel.utils.{ CustomObjectIdSerializer, getTimeNow }

trait SentinelServletSpec extends MutableScalatraSpec
    with EmbeddedMongodbRunner
    with JsonMatchers {

  import SentinelServletSpec._

  sequential

  override def start() = {
    super[MutableScalatraSpec].start()
    super[EmbeddedMongodbRunner].start()
  }

  override def stop() = {
    super[MutableScalatraSpec].stop()
    super[EmbeddedMongodbRunner].stop()
  }

  implicit val formats = DefaultFormats + new CustomObjectIdSerializer

  def jsonBody: Option[JValue] = Try(parse(body)).toOption

  def apiMessage: Option[ApiMessage] = jsonBody.collect { case json => json.extract[ApiMessage] }

  // TODO: Use the specs2 built-in raw JSON matcher when we switch to specs2-3.6
  implicit def jsonBodyIsSized: Sized[Option[JValue]] = new Sized[Option[JValue]] {
    def size(t: Option[JValue]) = t match {
      case None => -1
      case Some(jvalue) => jvalue match {
        case JArray(list) => list.size
        case JObject(objects) => objects.size
        case JString(str) => str.length
        case otherwise => -1
      }
    }
  }

  object ExampleContext {

    trait CleanDatabase extends BeforeAfter {
      def before = resetDatabase()
      def after = resetDatabase()
    }

    trait CleanDatabaseWithUser extends CleanDatabase with UsersAdapter {

      lazy val mongo = dao

      def user = testUser

      override def before = {
        super.before
        addUser(user)
      }
    }
  }

  object SpecContext {

    trait BeforeAllAfterAll extends Specification {
      override def map(fs: =>Fragments) = Step(beforeAll()) ^ fs ^ Step(afterAll())
      protected def beforeAll()
      protected def afterAll()
    }

    trait CleanDatabase extends BeforeAllAfterAll {
      def beforeAll() = resetDatabase()
      def afterAll() = resetDatabase()
    }

    trait CleanDatabaseWithUser extends CleanDatabase with UsersAdapter {

      lazy val mongo = dao

      def user = testUser

      override def beforeAll() = {
        super.beforeAll()
        addUser(user)
      }
    }

    trait AfterRequest[T] extends CleanDatabaseWithUser {
      sequential

      private var _requestResponse: T = _

      def requestMethod: T

      def requestResponse = _requestResponse

      override def beforeAll() = {
        super.beforeAll()
        _requestResponse = requestMethod
      }
    }

    trait AfterRunUpload extends AfterRequest[ClientResponse] {
      def pipeline: String
      def runFile: File
      def uploadEndpoint = "/runs"
      def uploadParams = Seq(("userId", user.id), ("pipeline", pipeline))
      def uploadFile = Map("run" -> runFile)
      def uploadHeader = Map(HeaderApiKey -> user.activeKey)
      def requestMethod = post(uploadEndpoint, uploadParams, uploadFile, uploadHeader) { response }

      "after the summary file has been uploaded to an empty database" in {
        requestResponse.statusLine.code mustEqual 201
      }
    }
  }
}

object SentinelServletSpec {

  val testUser = User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false, getTimeNow)

}
