package nl.lumc.sasc.sentinel


import scala.util.Try

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.mutable.{ BeforeAfter, Specification }
import org.specs2.specification.{ Fragments, Step }

import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ ApiMessage, User }
import nl.lumc.sasc.sentinel.utils.{ CustomObjectIdSerializer, getTimeNow }

trait SentinelServletSpec extends MutableScalatraSpec with EmbeddedMongodbRunner {

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


  object ExampleContext {

    trait CleanDatabase extends BeforeAfter {
      def before = resetDb()
      def after = resetDb()
    }

    trait CleanDatabaseWithUser extends CleanDatabase with UsersAdapter {

      lazy val mongo = dbAccess

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
      def beforeAll() = resetDb()
      def afterAll() = resetDb()
    }

    trait CleanDatabaseWithUser extends CleanDatabase with UsersAdapter {

      lazy val mongo = dbAccess

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
  }
}

object SentinelServletSpec {

  val testUser = User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false, getTimeNow)

}
