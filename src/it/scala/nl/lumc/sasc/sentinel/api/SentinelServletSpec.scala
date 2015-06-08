package nl.lumc.sasc.sentinel.api

import scala.util.Try

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.specs2.MutableScalatraSpec
import org.scalatra.test.{ BytesPart, ClientResponse, Uploadable }
import org.specs2.data.Sized
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.{ Fragments, Step }

import nl.lumc.sasc.sentinel.{ EmbeddedMongodbRunner, HeaderApiKey }
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, getResourceBytes, getTimeNow }

trait SentinelServletSpec extends MutableScalatraSpec
    with EmbeddedMongodbRunner
    with JsonMatchers {

  sequential

  override def start() = {
    super[MutableScalatraSpec].start()
    super[EmbeddedMongodbRunner].start()
  }

  override def stop() = {
    super[MutableScalatraSpec].stop()
    super[EmbeddedMongodbRunner].stop()
  }

  implicit val formats = SentinelJsonFormats

  implicit class RichClientResponse(httpres: ClientResponse) {

    lazy val jsonBody: Option[JValue] = Try(parse(httpres.body)).toOption

    lazy val contentType = httpres.mediaType.getOrElse(failure("'Content-Type' not found in response header."))
  }

  def contentType = response.contentType

  def jsonBody = response.jsonBody

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

  implicit def jsonIsSized: Sized[JValue] =  new Sized[JValue] {
    def size(t: JValue) = t match {
      case JArray(list) => list.size
      case JObject(objects) => objects.size
      case JString(str) => str.length
      case otherwise => -1
    }
  }

  object Users {
    val avg = User("avg", "avg@test.id", "pwd1", "key1", verified = true, isAdmin = false, getTimeNow)
    val avg2 = User("avg2", "avg2@test.id", "pwd2", "key2", verified = true, isAdmin = false, getTimeNow)
    val admin = User("admin", "admin@test.id", "pwd3", "key3", verified = true, isAdmin = true, getTimeNow)
    val unverified = User("unv", "unv@test.id", "pwd4", "key4", verified = false, isAdmin = false, getTimeNow)
    def all = Set(avg, avg2, admin, unverified)
  }

  object Context {

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

      implicit def user: User = Users.avg
      implicit def users: Set[User] = Users.all

      override def beforeAll() = {
        super.beforeAll()
        users.foreach { addUser }
      }
    }

    trait PriorRequests extends BeforeAllAfterAll {
      sequential

      type Req = () => ClientResponse

      def priorRequest: Req = priorRequests.head

      def priorRequests: Seq[Req]

      lazy val priorResponse: ClientResponse = priorResponses.head

      lazy val priorResponses: Seq[ClientResponse] = priorRequests.map(f => f())

      def beforeAll() = {
        priorResponses
        ()
      }

      def afterAll() = {}
    }

    trait PriorRequestsClean extends PriorRequests with CleanDatabaseWithUser {

      override def beforeAll() = {
        super[CleanDatabaseWithUser].beforeAll()
        super[PriorRequests].beforeAll()
      }

      override def afterAll() = {
        super[PriorRequests].afterAll()
        super[CleanDatabaseWithUser].afterAll()
      }
    }

    trait PriorRunUploadClean extends PriorRequestsClean {
      def pipelineParam: String
      def uploadPayload: Uploadable
      def uploadUser = user
      def expectedUploadStatus = 201
      def uploadEndpoint = "/runs"
      def uploadParams = Seq(("userId", uploadUser.id), ("pipeline", pipelineParam))
      def uploadFile = Map("run" -> uploadPayload)
      def uploadHeader = Map(HeaderApiKey -> uploadUser.activeKey)
      def priorRequests = Seq(() => post(uploadEndpoint, uploadParams, uploadFile, uploadHeader) { response })

      s"after the user uploads the '$pipelineParam' summary file to an empty database" in {
        priorResponse.statusLine.code mustEqual expectedUploadStatus
      }
    }
  }
}

object SentinelServletSpec {

  def makeUploadable(resourceUrl: String): Uploadable = BytesPart(
    fileName = resourceUrl.split("/").last,
    content = getResourceBytes(resourceUrl))

  object SchemaExamples {

    object Gentrap {

      object V04 {

        // 1 sample, 1 lib
        lazy val SSampleSLib = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_lib.json")
        // 1 sample, 2 libs
        lazy val SSampleMLib = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_multi_lib.json")
        // 3 samples, (3, 2, 1) libs
        lazy val MSampleSLib = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")
        // 2 samples (1, 1) libs
        lazy val MSampleMLib = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_lib.json")
      }
    }

    lazy val Unsupported = makeUploadable("/schema_examples/unsupported.json")
    lazy val Invalid = makeUploadable("/schema_examples/invalid.json")
    lazy val Not = makeUploadable("/schema_examples/not.json")
  }
}
