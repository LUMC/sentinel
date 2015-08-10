/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.api

import scala.util.Try
import scala.concurrent._
import scala.concurrent.duration._

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
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, getResourceBytes, getUtcTimeNow }

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

  def patch[A](uri: String, params: Iterable[(String, String)], body: Array[Byte], headers: Map[String, String])
              (f: => A): A =
    submit("PATCH", uri, params, headers, body) { f }

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

    import nl.lumc.sasc.sentinel.models.User.hashPassword

    val avg =
      User("avg", "avg@test.id", hashPassword("0PwdAvg"), "key1", verified = true, isAdmin = false)
    val avg2 =
      User("avg2", "avg2@test.id", hashPassword("0PwdAvg2"), "key2", verified = true, isAdmin = false)
    val admin =
      User("admin", "admin@test.id", hashPassword("0PwdAdmin"), "key3", verified = true, isAdmin = true)
    val unverified =
      User("unv", "unv@test.id", hashPassword("0PwdUnverified"), "key4", verified = false, isAdmin = false)
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
        users.foreach { user => Await.result(addUser(user), 1000.milli) }
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

    class OptionsMethodTest(endpoint: String, allowedMethods: String) extends PriorRequests {

      def request = () => options(endpoint) { response }
      def priorRequests = Seq(request)

      "return status 200" in {
        priorResponse.status mustEqual 200
      }

      "return the expected response header" in {
        priorResponse.headers must havePair("Access-Control-Allow-Methods" -> Seq(allowedMethods))
      }

      "return an empty body" in {
        priorResponse.body must beEmpty
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

        // 1 sample, 1 rg
        lazy val SSampleSRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_rg.json")
        // 1 sample, 2 rgs
        lazy val SSampleMRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_multi_rg.json")
        // 2 samples (1, 1) rgs
        lazy val MSampleSRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_rg.json")
        // 3 samples, (3, 2, 1) rgs
        lazy val MSampleMRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_rg.json")
        // 3 samples (3: single, 1: single, 2: paired) rgs
        lazy val MSampleMRGMixedLib =
          makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_rg_mixedlib.json")
      }
    }

    lazy val Plain = makeUploadable("/schema_examples/plain.json")
    lazy val UnsupportedCompressed = makeUploadable("/schema_examples/plain.json.gz")
    lazy val Invalid = makeUploadable("/schema_examples/invalid.json")
    lazy val Not = makeUploadable("/schema_examples/not.json")
  }
}
