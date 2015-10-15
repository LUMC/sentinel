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

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.json4s._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.{ Payloads, User }
import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.testing.{ SentinelServletSpec, UserExamples }
import nl.lumc.sasc.sentinel.utils.reflect.makeDelayedProcessor

class RunsControllerSpec extends SentinelServletSpec {

  /** Overridden stop method that deletes any created temporary directory. */
  override def stop(): Unit = {
    deleteDirectory(tempDir)
    super.stop()
  }

  /** Helper method for creating temporary directories. */
  protected lazy val tempDir = Files.createTempDir()

  /** Helper method for creating named temporary files. */
  protected def createTempFile(name: String): File = new File(tempDir, name)

  /** Helper method to create file with arbitrary size. */
  protected def fillFile(file: File, size: Long): File = {
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(size)
    raf.close()
    file
  }

  /** Upload context for plain summary files. */
  class PlainUploadContext extends Context.PriorRunUploadClean {
    def uploadSet = UploadSet(users.avg, SummaryExamples.Plain, "plain")
    def priorRequests = Seq(uploadSet.request)
  }

  /** Upload context for plain summary files. */
  class MapleUploadContext extends Context.PriorRunUploadClean {
    def uploadSet = UploadSet(users.avg2, SummaryExamples.Maple.MSampleMRG, "maple")
    def priorRequests = Seq(uploadSet.request)
  }

  /** Uploads plain first, then maple. */
  class PlainThenMapleUploadContext extends Context.PriorRunUploadClean {
    def uploadSet1 = UploadSet(users.avg, SummaryExamples.Plain, "plain")
    def uploadSet2 = UploadSet(users.avg2, SummaryExamples.Maple.MSampleMRG, "maple")
    def priorRequests = Seq(uploadSet1, uploadSet2).map(_.request)
  }

  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.maple.MapleRunsProcessor],
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor])
  val servlet = new RunsController
  val baseEndpoint = "/runs"
  addServlet(servlet, s"$baseEndpoint/*")

  s"OPTIONS '$baseEndpoint'" >> {
    br
    "when using the default parameter should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint", "GET,HEAD,POST")
    }
  }

  s"POST '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the pipeline is not specified should" >> inline {

      new Context.PriorRequests {
        def request = () => post(endpoint, Seq(("userId", UserExamples.avg.id))) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Pipeline not specified.")
        }
      }
    }

    "when the request body is empty" >> inline {

      new Context.PriorRequests {
        def request = () => post(endpoint, Seq(("userId", UserExamples.avg.id), ("pipeline", "plain"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Run summary file not specified.")
        }
      }
    }

    "when an invalid pipeline is specified should" >> inline {

      val fileMap = Map("run" -> SummaryExamples.Plain)

      new Context.PriorRequests {

        def request = () => post(endpoint, Seq(("userId", UserExamples.avg.id), ("pipeline", "devtest")), fileMap) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Pipeline parameter is invalid.")
          priorResponse.body must /("hints") /# 0 / "Valid values are .+".r
        }
      }
    }

    "using the 'plain' pipeline summary file" >> {
      br

      val pipeline = "plain"
      def fileMap = Map("run" -> SummaryExamples.Plain)

      "when a run summary that passes all validation is uploaded should" >> inline {

        new Context.PriorRequestsClean {

          def request = () => post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)),
            fileMap, Map(HeaderApiKey -> user.activeKey)) { response }
          def priorRequests = Seq(request)

          "return status 201" in {
            priorResponse.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("creationTimeUtc" -> ".+".r)
            priorResponse.body must /("nReadGroups" -> 0)
            priorResponse.body must /("nSamples" -> 0)
            priorResponse.body must /("pipeline" -> "plain")
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must /("uploaderId" -> user.id)
            priorResponse.body must not /("annotIds" -> ".+".r)
            priorResponse.body must not /("refId" -> ".+".r)
            priorResponse.body must not /("sampleIds" -> ".+".r)
            priorResponse.body must not /("libIds" -> ".+".r)
          }
        }
      }

      "when a compressed run summary that passes all validation is uploaded should" >> inline {

        new Context.PriorRequestsClean {

          def fileMap = Map("run" -> SummaryExamples.PlainCompressed)
          def request = () => post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)),
            fileMap, Map(HeaderApiKey -> user.activeKey)) { response }
          def priorRequests = Seq(request)

          "return status 201" in {
            priorResponse.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("creationTimeUtc" -> ".+".r)
            priorResponse.body must /("nReadGroups" -> 0)
            priorResponse.body must /("nSamples" -> 0)
            priorResponse.body must /("pipeline" -> "plain")
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must /("uploaderId" -> user.id)
            priorResponse.body must not /("annotIds" -> ".+".r)
            priorResponse.body must not /("refId" -> ".+".r)
            priorResponse.body must not /("sampleIds" -> ".+".r)
            priorResponse.body must not /("libIds" -> ".+".r)
          }
        }
      }

      "when the same run summary is uploaded more than once by different users should" >> inline {

        new Context.PriorRequestsClean {

          def request1 = () =>
            post(endpoint, Seq(("userId", users.avg2.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> users.avg2.activeKey)) { response }
          def request2 = () =>
            post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> user.activeKey)) { response }
          def priorRequests = Seq(request1, request2)

          "return status 201 for the first upload" in {
            priorResponses.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            priorResponses.head.contentType mustEqual  "application/json"
            priorResponses.head.body must /("creationTimeUtc" -> ".+".r)
            priorResponses.head.body must /("nReadGroups" -> 0)
            priorResponses.head.body must /("nSamples" -> 0)
            priorResponses.head.body must /("pipeline" -> "plain")
            priorResponses.head.body must /("runId" -> """\S+""".r)
            priorResponses.head.body must /("uploaderId" -> users.avg2.id)
            priorResponses.head.body must not /("annotIds" -> ".+".r)
            priorResponses.head.body must not /("refId" -> ".+".r)
            priorResponses.head.body must not /("sampleIds" -> ".+".r)
            priorResponses.head.body must not /("libIds" -> ".+".r)
          }

          "return status 201 for the second upload" in {
            priorResponses.last.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the second upload" in {
            priorResponses.last.contentType mustEqual  "application/json"
            priorResponses.last.body must /("creationTimeUtc" -> ".+".r)
            priorResponses.last.body must /("nReadGroups" -> 0)
            priorResponses.last.body must /("nSamples" -> 0)
            priorResponses.last.body must /("pipeline" -> "plain")
            priorResponses.last.body must /("runId" -> """\S+""".r)
            priorResponses.last.body must /("uploaderId" -> user.id)
            priorResponses.last.body must not /("annotIds" -> ".+".r)
            priorResponses.last.body must not /("refId" -> ".+".r)
            priorResponses.last.body must not /("sampleIds" -> ".+".r)
            priorResponses.last.body must not /("libIds" -> ".+".r)
          }
        }
      }

      "when the user ID is not specified should" >> inline {

        new Context.PriorRequestsClean {

          def request = () => post(endpoint, Seq(("pipeline", pipeline)), fileMap) { response }
          def priorRequests = Seq(request)

          "return status 400" in {
            priorResponse.status mustEqual 400
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "User ID not specified.")
          }
        }
      }

      "when a non-JSON file is uploaded should" >> inline {

        new Context.PriorRequestsClean {

          def fileMap = Map("run" -> SummaryExamples.Not)
          def request = () =>
            post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> user.activeKey)) { response }
          def priorRequests = Seq(request)

          "return status 400" in {
            priorResponse.status mustEqual 400
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "JSON is invalid.")
            priorResponse.body must /("hints") /# 0 / "File is not JSON."
          }
        }
      }

      "when an invalid JSON run summary is uploaded should" >> inline {

        new Context.PriorRequestsClean {

          def fileMap = Map("run" -> SummaryExamples.Invalid)
          def request = () =>
            post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> user.activeKey)) { response }
          def priorRequests = Seq(request)

          "return status 400" in {
            priorResponse.status mustEqual 400
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "JSON is invalid.")
            priorResponse.body must /("hints") /# 0 / startWith("error: instance failed to match")
          }
        }
      }

      "when the same run summary is uploaded more than once by the same users should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> user.activeKey)

          def request1 = () => post(endpoint, params, fileMap, headers) { response }
          def request2 = () => post(endpoint, params, fileMap, headers) { response }
          def priorRequests = Seq(request1, request2)

          "return status 201 for the first upload" in {
            priorResponses.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            priorResponses.head.contentType mustEqual  "application/json"
            priorResponses.head.body must /("creationTimeUtc" -> ".+".r)
            priorResponses.head.body must /("nReadGroups" -> 0)
            priorResponses.head.body must /("nSamples" -> 0)
            priorResponses.head.body must /("pipeline" -> "plain")
            priorResponses.head.body must /("runId" -> """\S+""".r)
            priorResponses.head.body must /("uploaderId" -> user.id)
            priorResponses.head.body must not /("annotIds" -> ".+".r)
            priorResponses.head.body must not /("refId" -> ".+".r)
            priorResponses.head.body must not /("sampleIds" -> ".+".r)
            priorResponses.head.body must not /("libIds" -> ".+".r)
          }

          "return status 409 for the second upload" in {
            priorResponses.last.status mustEqual 409
          }

          "return a JSON object containing the expected message for the second upload" in {
            priorResponses.last.contentType mustEqual  "application/json"
            priorResponses.head.jsonBody must beSome.like { case json =>
              val id = (json \ "runId").extract[String]
              priorResponses.last.body must /("message" -> "Run summary already uploaded.")
              priorResponses.last.body must /("hints") /# 0 / s"Existing ID: $id."
            }
          }

        }
      }

      "when a run summary is uploaded more than once (uncompressed then compressed) by the same users should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> user.activeKey)

          def request1 = () => post(endpoint, params, fileMap, headers) { response }
          def request2 = () => post(endpoint, params,
            Map("run" -> SummaryExamples.PlainCompressed), headers) { response }
          def priorRequests = Seq(request1, request2)

          "return status 201 for the first upload" in {
            priorResponses.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            priorResponses.head.contentType mustEqual  "application/json"
            priorResponses.head.body must /("creationTimeUtc" -> ".+".r)
            priorResponses.head.body must /("nReadGroups" -> 0)
            priorResponses.head.body must /("nSamples" -> 0)
            priorResponses.head.body must /("pipeline" -> "plain")
            priorResponses.head.body must /("runId" -> """\S+""".r)
            priorResponses.head.body must /("uploaderId" -> user.id)
            priorResponses.head.body must not /("annotIds" -> ".+".r)
            priorResponses.head.body must not /("refId" -> ".+".r)
            priorResponses.head.body must not /("sampleIds" -> ".+".r)
            priorResponses.head.body must not /("libIds" -> ".+".r)
          }

          "return status 409 for the second upload" in {
            priorResponses.last.status mustEqual 409
          }

          "return a JSON object containing the expected message for the second upload" in {
            priorResponses.last.contentType mustEqual  "application/json"
            priorResponses.head.jsonBody must beSome.like { case json =>
              val id = (json \ "runId").extract[String]
              priorResponses.last.body must /("message" -> "Run summary already uploaded.")
              priorResponses.last.body must /("hints") /# 0 / s"Existing ID: $id."
            }
          }

        }
      }

      s"when the user does not provide the $HeaderApiKey header should" >> inline {

        new Context.PriorRequestsClean {

          def request = () => post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap) { response }
          def priorRequests = Seq(request)

          "return status 401" in {
            priorResponse.status mustEqual 401
          }

          "return the challenge response header key" in {
            priorResponse.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }


          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "Authentication required to access resource.")
          }
        }
      }

      s"when the provided $HeaderApiKey does not match the one owned by the user should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> (user.activeKey + "nono"))
          def request = () => post(endpoint, params, fileMap, headers) { response }
          def priorRequests = Seq(request)

          "return status 401" in {
            priorResponse.status mustEqual 401
          }

          "return the challenge response header key" in {
            priorResponse.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }


          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "Authentication required to access resource.")
          }
        }
      }

      s"when an unverified user uploads a run summary" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", users.unverified.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> users.unverified.activeKey)
          def request = () => post(endpoint, params, fileMap, headers) { response }
          def priorRequests = Seq(request)

          "return status 403" in {
            priorResponse.status mustEqual 403
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "Unauthorized to access resource.")
          }
        }
      }

      s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB should" >> inline {

        new Context.PriorRequestsClean {

          lazy val bigFile = createTempFile("bigFile.json")

          override def beforeAll() = {
            fillFile(bigFile, MaxRunSummarySize + 100)
            super.beforeAll()
          }

          override def afterAll() = {
            super.afterAll()
            deleteQuietly(bigFile)
          }

          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def fileMap = Map("run" -> bigFile)
          def headers = Map(HeaderApiKey -> user.activeKey)
          def request = () => post(endpoint, params, fileMap, headers) { response }
          def priorRequests = Seq(request)

          "return status 413" in {
            priorResponse.status mustEqual 413
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> s"Run summary exceeded maximum allowed size of $MaxRunSummarySizeMb MB.")
          }
        }
      }
    }

    "using a pipeline summary file with samples and read groups ('maple')" >> {
      br

      def params(implicit user: User) = Seq(("userId", user.id), ("pipeline", "maple"))
      def headers(implicit user: User) = Map(HeaderApiKey -> user.activeKey)

      "when a multi sample, multi read group summary is uploaded to an empty database should" >> inline {

        new Context.PriorRequestsClean {

          def request = () =>
            post(endpoint, params, Map("run" -> SummaryExamples.Maple.MSampleMRG), headers) { response }
          def priorRequests = Seq(request)

          "return status 201" in {
            priorResponse.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must /("uploaderId" -> user.id)
            priorResponse.body must /("pipeline" -> "maple")
            priorResponse.body must /("nSamples" -> 2)
            priorResponse.body must /("nReadGroups" -> 3)
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must not /("sampleIds" -> ".+".r)
            priorResponse.body must not /("libIds" -> ".+".r)
          }
        }
      }

    }
  }

  s"GET '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the database is empty" >> inline {

      new Context.CleanDatabaseWithUser {

        "when the user authenticates correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 200" in {
            get(endpoint, params, headers) { status mustEqual 200 }
          }

          "return an empty JSON list" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              jsonBody must haveSize(0)
            }
          }
        }

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.UnspecifiedUserIdError.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint, params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint, params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthenticationError.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", users.unverified.id))
          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthorizationError.message)
            }
          }
        }
      }
    }

    "using the 'plain' and the 'maple' run summary files" >> inline {

      new PlainThenMapleUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.UnspecifiedUserIdError.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint, params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint, params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthenticationError.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", users.unverified.id))
          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthorizationError.message)
            }
          }
        }

        "when the user authenticates correctly" >> {
          br

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "and queries with the default parameter" should {

            "return status 200" in {
              get(endpoint, params, headers) { status mustEqual 200 }
            }

            "return a JSON list containing a single run object with the expected payload" in {
              get(endpoint, params, headers) {
                contentType mustEqual "application/json"
                jsonBody must haveSize(1)
                body must /#(0) */("runId" -> """\S+""".r)
                body must /#(0) */("uploaderId" -> user.id)
                body must /#(0) */("pipeline" -> "plain")
                body must /#(0) */("nSamples" -> 0)
                body must /#(0) */("nReadGroups" -> 0)
                body must not /("sampleIds" -> ".+".r)
                body must not /("libIds" -> ".+".r)
              }
            }
          }

          "and selects for a pipeline he/she has not uploaded" should {

            "return status 200" in {
              get(endpoint, params :+ ("pipelines", "maple"), headers) { status mustEqual 200 }
            }

            "return an empty JSON list" in {
              get(endpoint, params :+ ("pipelines", "maple"), headers) {
                contentType mustEqual "application/json"
                jsonBody must haveSize(0)
              }
            }
          }

          "and gives an incorrect pipeline parameter" should {

            "return status 400" in {
              get(endpoint, params :+ ("pipelines", "nonexistent"), headers) { status mustEqual 400 }
            }

            "return a JSON object of the expected message" in {
              get(endpoint, params :+ ("pipelines", "nonexistent"), headers) {
                contentType mustEqual "application/json"
                body must /("message" -> "One or more pipeline is invalid.")
                body must /("hints") /# 0 / "invalid pipelines: nonexistent."
              }
            }
          }
        }
      }
    }
  }

  s"OPTIONS '$baseEndpoint/:runId'" >> {
    br
    "when using the default parameter should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint/runId", "DELETE,GET,HEAD,PATCH")
    }
  }

  s"GET '$baseEndpoint/:runId'" >> {
    br

    def endpoint(uploadedRunId: String) = s"$baseEndpoint/$uploadedRunId"

    "using the 'plain' and the 'maple' run summary files" >> inline {

      new PlainThenMapleUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 400" in {
            get(endpoint(uploadedRunId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(uploadedRunId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.UnspecifiedUserIdError.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint(uploadedRunId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint(uploadedRunId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthenticationError.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", users.unverified.id))
          val headers = Map(HeaderApiKey -> users.unverified.activeKey)

          "return status 403" in {
            get(endpoint(uploadedRunId), params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthorizationError.message)
            }
          }
        }

        "when the user authenticates correctly" >> {
          br

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "and queries a run he/she uploaded" >> {
            br

            "with the default parameter" should {

              "return status 200" in {
                get(endpoint(uploadedRunId), params, headers) { status mustEqual 200 }
              }

              "return a JSON object of the run data" in {
                get(endpoint(uploadedRunId), params, headers) {
                  contentType mustEqual "application/json"
                  body must /("runId" -> uploadedRunId)
                  body must /("uploaderId" -> user.id)
                  body must /("nSamples" -> 0)
                  body must /("nReadGroups" -> 0)
                  body must /("pipeline" -> "plain")
                  body must not /("sampleIds" -> ".+".r)
                  body must not /("libIds" -> ".+".r)
                }
              }
            }

            "and sets the download parameter to some true values which" can {

              Seq("1", "yes", "true", "ok") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return the expected Content-Disposition header" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadSet1.payload.fileName))
                    }
                  }

                  "return the uploaded summary file" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/octet-stream"
                      body mustEqual new String(uploadSet1.payload.content)
                    }
                  }
                }
              }
            }

            "and sets the download parameter to some false values which" can {

              Seq("0", "no", "false", "none", "null", "nothing") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return a JSON object of the run data" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/json"
                      body must /("runId" -> uploadedRunId)
                      body must /("uploaderId" -> user.id)
                      body must not /("sampleIds" -> ".+".r)
                      body must not /("libIds" -> ".+".r)
                      body must /("nSamples" -> 0)
                      body must /("nReadGroups" -> 0)
                      body must /("pipeline" -> "plain")
                    }
                  }
                }
              }
            }


          }

          "and queries a run he/she did not upload" should {

            lazy val uploadedRunId2 = uploadedRunIds.tail.head

            "return status 404" in {
              get(endpoint(uploadedRunId2), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in {
              get(endpoint(uploadedRunId2), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> Payloads.RunIdNotFoundError.message)
              }
            }
          }

          "and queries a run with an invalid ID" should {

            val invalidId = "nonexistendId"

            "return status 404" in {
              get(endpoint(invalidId), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in  {
              get(endpoint(invalidId), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> Payloads.RunIdNotFoundError.message)
              }
            }
          }
        }

        "when an admin authenticates correctly" >> {
          br

          val params = Seq(("userId", users.admin.id))
          val headers = Map(HeaderApiKey -> users.admin.activeKey)

          "and queries a run he/she did not upload" >> {
            br

            "with the default parameter" should {

              "return status 200" in {
                get(endpoint(uploadedRunId), params, headers) { status mustEqual 200 }
              }

              "return a JSON object of the run data" in {
                get(endpoint(uploadedRunId), params, headers) {
                  contentType mustEqual "application/json"
                  body must /("runId" -> uploadedRunId)
                  body must /("uploaderId" -> user.id)
                  body must /("nSamples" -> 0)
                  body must /("nReadGroups" -> 0)
                  body must /("pipeline" -> "plain")
                  body must not /("sampleIds" -> ".+".r)
                  body must not /("libIds" -> ".+".r)
                }
              }
            }

            "and sets the download parameter to some true values which" can {

              Seq("1", "yes", "true", "ok") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return the expected Content-Disposition header" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadSet1.payload.fileName))
                    }
                  }

                  "return the uploaded summary file" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/octet-stream"
                      body mustEqual new String(uploadSet1.payload.content)
                    }
                  }
                }
              }
            }

            "and sets the download parameter to some false values which" can {

              Seq("0", "no", "false", "none", "null", "nothing") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return a JSON object of the run data" in {
                    get(endpoint(uploadedRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/json"
                      body must /("runId" -> uploadedRunId)
                      body must /("uploaderId" -> user.id)
                      body must not /("sampleIds" -> ".+".r)
                      body must not /("libIds" -> ".+".r)
                      body must /("nSamples" -> 0)
                      body must /("nReadGroups" -> 0)
                      body must /("pipeline" -> "plain")
                    }
                  }
                }
              }
            }
          }

          "and queries a run with an invalid ID" should {

            val invalidId = "nonexistendId"

            "return status 404" in {
              get(endpoint(invalidId), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in  {
              get(endpoint(invalidId), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> Payloads.RunIdNotFoundError.message)
              }
            }
          }
        }
      }
    }
  }

  s"DELETE '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    "using the 'plain' run summary file" >> inline {

      new PlainUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 400" in {
            delete(endpoint(uploadedRunId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(uploadedRunId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.UnspecifiedUserIdError.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadSet.payload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadSet.payload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }

        "when the run ID is not specified" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 400" in {
            delete(endpoint(""), params, headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(""), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.UnspecifiedRunIdError.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadSet.payload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadSet.payload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            delete(endpoint(uploadedRunId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            delete(endpoint(uploadedRunId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> Payloads.AuthenticationError.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadSet.payload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadSet.payload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }
      }
    }

    "when the user authenticates correctly" >> {
      br

      "with the default parameters for the 'plain' pipeline should" >> inline {

        new PlainUploadContext {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)
          def request = () => delete(endpoint(uploadedRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            priorResponses.last.contentType mustEqual "application/json"
            priorResponses.last.body must /("runId" -> uploadedRunId)
            priorResponses.last.body must /("uploaderId" -> user.id)
            priorResponses.last.body must not /("sampleIds" -> ".+".r)
            priorResponses.last.body must not /("libIds" -> ".+".r)
            priorResponses.last.body must /("nSamples" -> 0)
            priorResponses.last.body must /("nReadGroups" -> 0)
            priorResponses.last.body must /("pipeline" -> "plain")
            priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
                status mustEqual 404
                contentType mustEqual "application/json"
                body must not /("runId" -> ".+".r)
                body must /("message" -> Payloads.RunIdNotFoundError.message)
              }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Resource already deleted.")
            }
          }
        }
      }

      "with the default parameters for the 'maple' pipeline (multi sample, multi read group) should" >> inline {

        new MapleUploadContext {

          override def user = uploadSet.uploader
          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)
          def request = () => delete(endpoint(uploadedRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(uploadedRunId), params, headers) {
              priorResponses.last.contentType mustEqual "application/json"
              priorResponses.last.body must /("runId" -> uploadedRunId)
              priorResponses.last.body must /("uploaderId" -> user.id)
              priorResponses.last.body must not /("sampleIds" -> ".+".r)
              priorResponses.last.body must not /("readGroupIds" -> ".+".r)
              priorResponses.last.body must /("nSamples" -> 2)
              priorResponses.last.body must /("nReadGroups" -> 3)
              priorResponses.last.body must /("pipeline" -> "maple")
              priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 again when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Resource already deleted.")
            }
          }
        }
      }
    }

    "when an admin authenticates correctly" >> {
      br

      "with the default parameters for the 'plain' pipeline should" >> inline {

        new PlainUploadContext {

          val params = Seq(("userId", users.admin.id))
          val headers = Map(HeaderApiKey -> users.admin.activeKey)
          def request = () => delete(endpoint(uploadedRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            priorResponses.last.contentType mustEqual "application/json"
            priorResponses.last.body must /("runId" -> uploadedRunId)
            priorResponses.last.body must /("uploaderId" -> user.id)
            priorResponses.last.body must not /("sampleIds" -> ".+".r)
            priorResponses.last.body must not /("libIds" -> ".+".r)
            priorResponses.last.body must /("nSamples" -> 0)
            priorResponses.last.body must /("nReadGroups" -> 0)
            priorResponses.last.body must /("pipeline" -> "plain")
            priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Resource already deleted.")
            }
          }
        }
      }

      "with the default parameters for the 'maple' pipeline (multi sample, multi read group) should" >> inline {

        new MapleUploadContext {

          val params = Seq(("userId", users.admin.id))
          val headers = Map(HeaderApiKey -> users.admin.activeKey)
          def request = () => delete(endpoint(uploadedRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(uploadedRunId), params, headers) {
              priorResponses.last.contentType mustEqual "application/json"
              priorResponses.last.body must /("runId" -> uploadedRunId)
              priorResponses.last.body must /("uploaderId" -> uploadSet.uploader.id)
              priorResponses.last.body must not /("sampleIds" -> ".+".r)
              priorResponses.last.body must not /("readGroupIds" -> ".+".r)
              priorResponses.last.body must /("nSamples" -> 2)
              priorResponses.last.body must /("nReadGroups" -> 3)
              priorResponses.last.body must /("pipeline" -> "maple")
              priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$uploadedRunId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 again when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(uploadedRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Resource already deleted.")
            }
          }
        }
      }
    }
  }
}
