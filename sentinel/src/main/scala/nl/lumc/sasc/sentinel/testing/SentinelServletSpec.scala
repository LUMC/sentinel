/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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
import org.scalatra.test.{ ClientResponse, Uploadable }
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.core.Fragments
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.adapters.UsersAdapter
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

/** Base trait for Sentinel servlet testing. */
trait SentinelServletSpec extends MutableScalatraSpec
    with IntegrationTestImplicits
    with SwaggerProvider
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

  // FIXME: Somehow beforeAll is called after all tests are executed (which doesn't make sense!) So we disable it for
  //        now and start manually.
  override def beforeAll = {}
  start()

  /** Helper method to populate the database with users. */
  protected def populateUsers(users: Set[User]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val adapter = new UsersAdapter { val mongo = dao }
    Await.ready(Future.sequence(users.map { adapter.addUser }), Duration.Inf)
  }

  /** Convenience method for testing content type. */
  def contentType = response.contentType

  /** Convenience method for testing JSON response body. */
  def jsonBody = response.jsonBody

  /** HTTP PATCH method for testing that accepts params, body, and headers. */
  def patch[A](uri: String, params: Iterable[(String, String)], body: Array[Byte], headers: Map[String, String])(f: => A): A =
    submit("PATCH", uri, params, headers, body) { f }

  /** Type aliast for HTTP request functions. */
  type ReqFunc = () => ClientResponse

  /** Helper container for an upload. */
  sealed case class UploadSet(uploader: User, payload: SentinelTestPart with Uploadable,
                              showUnitsLabels: Boolean = false,
                              uploadEndpoint: String = "/runs") {

    /** Helper method for creating upload requests. */
    lazy val reqFunc: ReqFunc = {
      val params = Seq(("userId", uploader.id), ("pipeline", payload.pipelineName)) ++
        (if (showUnitsLabels) Seq(("showUnitsLabels", "true")) else Seq())
      val headers = Map(HeaderApiKey -> uploader.activeKey)
      () => post(uploadEndpoint, params, Map("run" -> payload.toBytesPart), headers) { response }
    }
  }

  /** HTTP requests test context. */
  trait HttpContextLike {

    /** HTTP requests to send. */
    def reqFuncs: NonEmptyList[ReqFunc]

    /** Returned response objects. */
    lazy val reps: NonEmptyList[ClientResponse] = reqFuncs.map(rf => rf())

    /** First response. */
    lazy val rep: ClientResponse = reps.head
  }

  /** HTTP requests test context. */
  sealed case class HttpContext(reqFuncs: NonEmptyList[ReqFunc]) extends HttpContextLike {

    /** Alternative constructor for single requests. */
    def this(req: ReqFunc) = this(NonEmptyList(req))
  }

  /** Companion for [[HttpContext]] with overloaded apply method. */
  object HttpContext {
    def apply(req: ReqFunc) = new HttpContext(req)
  }

  /** Summary file upload test context. */
  sealed case class UploadContext(sets: NonEmptyList[UploadSet]) extends HttpContextLike {

    /** Alternative constructor for single uploads. */
    def this(upload: UploadSet) = this(NonEmptyList(upload))

    /** Returned response objects. */
    lazy val reqFuncs: NonEmptyList[ReqFunc] = sets.map(_.reqFunc)

    /** Uploaded summary run IDs. */
    lazy val runIds: NonEmptyList[String] = reps.map { pr => (parse(pr.body) \ "runId").extract[String] }

    /** Uploaded summary sample labels. */
    lazy val sampleLabelsAll: NonEmptyList[Map[String, Map[String, Any]]] = sets.zip(reps)
      .map {
        case (uplset, uplrep) =>
          if (!uplset.showUnitsLabels) Map.empty
          else (parse(uplrep.body) \ "sampleLabels").extract[Map[String, Map[String, Any]]]
      }

    /** Uploaded summary sample IDs. */
    lazy val sampleIdsAll: NonEmptyList[List[String]] = sampleLabelsAll.map(_.keys.toList)

    /** Uploaded summary sample labels. */
    lazy val readGroupLabelsAll: NonEmptyList[Map[String, Map[String, Any]]] = sets.zip(reps)
      .map {
        case (uplset, uplrep) =>
          if (!uplset.showUnitsLabels) Map.empty
          else (parse(uplrep.body) \ "readGroupLabels").extract[Map[String, Map[String, Any]]]
      }

    /** Uploaded summary sample IDs. */
    lazy val readGroupIdsAll: NonEmptyList[List[String]] = readGroupLabelsAll.map(_.keys.toList)

    /** First run ID. */
    lazy val runId: String = runIds.head

    /** First run's sample labels. */
    lazy val sampleLabels: Map[String, Map[String, Any]] = sampleLabelsAll.head

    /** First run's sample IDs. */
    lazy val sampleIds: List[String] = sampleIdsAll.head

    /** First run's read group labels. */
    lazy val readGroupLabels: Map[String, Map[String, Any]] = readGroupLabelsAll.head

    /** First run's read group IDs. */
    lazy val readGroupIds: List[String] = readGroupIdsAll.head

    /** Uploading users. */
    lazy val uploaders: NonEmptyList[User] = sets.map(_.uploader)

    /** First uploader. */
    lazy val uploader: User = uploaders.head
  }

  /** Companion for [[UploadContext]] with overloaded apply method. */
  object UploadContext {
    def apply(upload: UploadSet) = new UploadContext(upload)
  }

  /** Helper function to append request functions to existing upload context. */
  def prependUploads(uploads: NonEmptyList[UploadSet])(reqf: ReqFunc) =
    HttpContext(uploads.map(_.reqFunc) append reqf.wrapNel)

  /** Specific test contexts. */
  object ctx {

    // TODO: How to make these contexts composable with each other?

    /** Context for tests with a clean database. */
    // format: OFF
    def cleanDb(populate: Boolean = false, users: Set[User] = UserExamples.all, dao: MongodbAccessObject)
               (f: Unit => Fragments): Fragments = {
      // format: ON
      dao.reset()
      if (populate) {
        implicit val ec: ExecutionContext = ExecutionContext.global
        val adapter = new UsersAdapter { val mongo = dao }
        Await.ready(Future.sequence(users.map { adapter.addUser }), Duration.Inf)
      }
      addFragments(f(()))
    }

    /** Context for testing HTTP OPTION method. */
    def optionsReq(endpoint: String, allowedMethods: String): Fragments = {
      val rep = options(endpoint) { response }

      "return status 200" in {
        rep.status mustEqual 200
      }

      "return the expected response header" in {
        rep.headers must havePair("Access-Control-Allow-Methods" -> Seq(allowedMethods))
      }

      "return an empty body" in {
        rep.body must beEmpty
      }
    }

    /** Context for tests with prior HTTP requests. */
    def priorReqs(httpContext: HttpContext)(f: HttpContext => Fragments): Fragments = {
      httpContext.reps
      addFragments(f(httpContext))
    }

    /** Context for tests with prior HTTP requests on a clean database. */
    // format: OFF
    def priorReqsOnCleanDb(httpContext: HttpContextLike, populate: Boolean = false,
                           users: Set[User] = UserExamples.all, dao: MongodbAccessObject = dao)
                          (f: HttpContextLike => Fragments): Fragments = {
      // format: ON
      dao.reset()
      if (populate) populateUsers(users)
      httpContext.reps
      addFragments(f(httpContext))
    }
  }
}

/** Container for Swagger value provider. */
trait SwaggerProvider {

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

  /** Implicit swagger value for testing. */
  implicit val swagger: Swagger = new TestSwagger
}
