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
package nl.lumc.sasc.sentinel.testing

import scala.concurrent._
import scala.concurrent.duration._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.swagger._
import org.scalatra.test.specs2.MutableScalatraSpec
import org.scalatra.test.{ BytesPart, ClientResponse, Uploadable }
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.{ Fragments, Step }

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.adapters.UsersAdapter
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, readResourceBytes }

/** Base trait for Sentinel servlet testing. */
trait SentinelServletSpec extends MutableScalatraSpec
    with IntegrationTestImplicits
    with EmbeddedMongodbRunner
    with JsonMatchers {

  sequential

  /** Overridden start method that also starts the MongoDB runner. */
  override def start() = {
    super[MutableScalatraSpec].start()
    super[EmbeddedMongodbRunner].start()
  }

  /** Overridden stop method that also stops the MongoDB runner. */
  override def stop() = {
    super[MutableScalatraSpec].stop()
    super[EmbeddedMongodbRunner].stop()
  }

  /** Default swagger ApiInfo for testing. */
  class TestSwagger extends Swagger(apiInfo = TestSwagger.apiInfo, apiVersion = "TEST",
    swaggerVersion = Swagger.SpecVersion)

  /** General API info. */
  object TestSwagger {
    val apiInfo = ApiInfo(
      title = "placeholder",
      description = "placeholder",
      termsOfServiceUrl = "http://placehold.er",
      contact = "test@placehold.er",
      license = "",
      licenseUrl = "")
  }

  implicit val swagger: Swagger = new TestSwagger

  /** Default JSON formats. */
  implicit protected val jsonFormats = SentinelJsonFormats

  /** Convenience method for testing content type. */
  def contentType = response.contentType

  /** Convenience method for testing JSON response body. */
  def jsonBody = response.jsonBody

  /** HTTP PATCH method for testing that accepts params, body, and headers. */
  def patch[A](uri: String, params: Iterable[(String, String)], body: Array[Byte], headers: Map[String, String])(f: => A): A =
    submit("PATCH", uri, params, headers, body) { f }

  /** Various context providers for integration tests. */
  object Context {

    /** Base trait for adding executions before and after all tests. */
    trait BeforeAllAfterAll extends Specification {
      override def map(fs: => Fragments) = Step(beforeAll()) ^ fs ^ Step(afterAll())
      protected def beforeAll()
      protected def afterAll()
    }

    /** Clean database test context. */
    trait CleanDatabase extends BeforeAllAfterAll {
      def beforeAll() = resetDatabase()
      def afterAll() = resetDatabase()
    }

    /** Clean database test context with pre-added users. */
    trait CleanDatabaseWithUser extends CleanDatabase with UsersAdapter {

      lazy val mongo = dao

      /** Default user. */
      implicit def user: User = users.avg

      /** Default set of users. */
      implicit def users = UserExamples

      override def beforeAll() = {
        super.beforeAll()
        Await.ready(Future.sequence(users.all.map { addUser }), Duration.Inf)
      }
    }

    /** Testing context with one or more requests sent before the actual test. */
    trait PriorRequests extends BeforeAllAfterAll {

      sequential

      /** Type alias for the request. */
      type Req = () => ClientResponse

      /** First of the prior requests. */
      def priorRequest: Req = priorRequests.head

      /** All prior requests. */
      def priorRequests: Seq[Req]

      /** Response of the first prior request. */
      lazy val priorResponse: ClientResponse = priorResponses.head

      /** All responses of all requests. */
      lazy val priorResponses: Seq[ClientResponse] = priorRequests.map(f => f())

      def beforeAll() = {
        priorResponses
        ()
      }

      def afterAll() = {}
    }

    /** Testing context with prior requests on a clean database populated with users. */
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

    /** Testing context with a prior run summary upload. */
    trait PriorRunUploadClean extends PriorRequestsClean {

      /** Helper container for an upload. */
      protected case class UploadSet(uploader: User, payload: Uploadable, pipelineName: String) {

        /** Helper method for creating upload requests. */
        lazy val request: Req = {
          val params = Seq(("userId", uploader.id), ("pipeline", pipelineName))
          val headers = Map(HeaderApiKey -> uploader.activeKey)
          () => post(uploadEndpoint, params, Map("run" -> payload), headers) { response }
        }
      }

      /** HTTP endpoint for the upload. */
      def uploadEndpoint = "/runs"

      /** Store all uploaded run ID values. */
      lazy val uploadedRunIds: Seq[String] = priorResponses.map { pr => (parse(pr.body) \ "runId").extract[String] }

      /** Helper method to retrieve the first uploaded Run ID. */
      lazy val uploadedRunId: String = uploadedRunIds.head
    }

    /** Convenience class for testing HTTP OPTION methods. */
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

  /**
   * Convenience method for creating uploadable object from a resource URL.
   *
   * @param resourceUrl Resource URL pointing to the summary file to upload.
   * @return Uploadable object.
   */
  def makeUploadable(resourceUrl: String): Uploadable = BytesPart(
    fileName = resourceUrl.split("/").last,
    content = readResourceBytes(resourceUrl))
}
