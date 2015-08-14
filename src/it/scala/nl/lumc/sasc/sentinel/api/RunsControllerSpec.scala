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
import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.{ CommonMessages, RunRecord, User }
import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.utils.reflect.runsProcessorMaker

class RunsControllerSpec extends SentinelServletSpec {

  override def stop(): Unit = {
    deleteDirectory(tempDir)
    super.stop()
  }

  protected lazy val tempDir = Files.createTempDir()

  protected def createTempFile(name: String): File = new File(tempDir, name)

  protected def fillFile(file: File, size: Long): File = {
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(size)
    raf.close()
    file
  }

  class PlainUploadContext extends Context.PriorRunUploadClean {
    def pipelineParam = "plain"
    def uploadPayload = SummaryExamples.Plain
    lazy val runId = (parse(priorResponse.body) \ "runId").extract[String]
  }

  class PlainThenGentrapUploadContext extends PlainUploadContext {

    def pipeline2 = "gentrap"
    def uploadParams2 = Seq(("userId", UserExamples.avg2.id), ("pipeline", pipeline2))
    def uploadFile2 = Map("run" -> uploadPayload2)
    def uploadHeader2 = Map(HeaderApiKey -> UserExamples.avg2.activeKey)
    def uploadPayload2 = LumcSummaryExamples.Gentrap.V04.SSampleSRG
    lazy val runId2 = (parse(priorResponses(1).body) \ "runId").extract[String]

    override def priorRequests = super.priorRequests ++ Seq(
      () => post(uploadEndpoint, uploadParams2, uploadFile2, uploadHeader2) { response }
    )

    s"and another user uploads the '$pipeline2' summary file" in {
      priorResponses.head.statusLine.code mustEqual 201
    }
  }

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04RunsProcessor],
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.plain.PlainRunsProcessor])
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
          priorResponse.body must /("hint" -> "Valid values are .+".r)
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
            post(endpoint, Seq(("userId", UserExamples.avg2.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response }
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
            priorResponses.head.body must /("uploaderId" -> UserExamples.avg2.id)
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
            priorResponse.body must /("message" -> "File is not JSON-formatted.")
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
            priorResponse.body must /("message" -> "JSON run summary is invalid.")
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
            priorResponses.last.body must /("message" -> "Run summary already uploaded.")
            priorResponses.head.jsonBody must beSome.like { case json =>
              priorResponses.last.body must /("hint") /("uploadedId" -> (json \ "runId").extract[String])
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
            priorResponses.last.body must /("message" -> "Run summary already uploaded.")
            priorResponses.head.jsonBody must beSome.like { case json =>
              priorResponses.last.body must /("hint") /("uploadedId" -> (json \ "runId").extract[String])
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

          def params = Seq(("userId", UserExamples.unverified.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)
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

    "using the 'gentrap' pipeline summary run file" >> {
      br

      def params(implicit user: User) = Seq(("userId", user.id), ("pipeline", "gentrap"))
      def headers(implicit user: User) = Map(HeaderApiKey -> user.activeKey)

      "when the v0.4 run summary (single sample, single lib) is uploaded to an empty database should" >> inline {

        new Context.PriorRequestsClean {

          def request = () =>
            post(endpoint, params, Map("run" -> LumcSummaryExamples.Gentrap.V04.SSampleSRG), headers) { response }
          def priorRequests = Seq(request)

          "return status 201" in {
            priorResponse.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must /("uploaderId" -> user.id)
            priorResponse.body must /("pipeline" -> "gentrap")
            priorResponse.body must /("nSamples" -> 1)
            priorResponse.body must /("nReadGroups" -> 1)
            priorResponse.body must /("runId" -> """\S+""".r)
            priorResponse.body must not /("sampleIds" -> ".+".r)
            priorResponse.body must not /("libIds" -> ".+".r)
            // TODO: use raw JSON matchers when we upgrade specs2
            priorResponse.jsonBody must beSome.like { case json => (json \ "annotIds") must haveSize(3) }
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

          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.UnspecifiedUserId.message)
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
              body must /("message" -> CommonMessages.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", UserExamples.unverified.id))
          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.Unauthorized.message)
            }
          }
        }
      }
    }

    "using the 'plain' and the 'gentrap' run summary files" >> inline {

      new PlainThenGentrapUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.UnspecifiedUserId.message)
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
              body must /("message" -> CommonMessages.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", UserExamples.unverified.id))
          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.Unauthorized.message)
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
                body must not /# 0 */ "refId"
                body must not /# 0 */ "annotIds"
              }
            }
          }

          "and selects for a pipeline he/she has not uploaded" should {

            "return status 200" in {
              get(endpoint, params :+ ("pipelines", "gentrap"), headers) { status mustEqual 200 }
            }

            "return an empty JSON list" in {
              get(endpoint, params :+ ("pipelines", "gentrap"), headers) {
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
                body must /("hint") / "invalid pipelines" /# 0 / "nonexistent"
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
      new Context.OptionsMethodTest(s"$baseEndpoint/runId", "DELETE,GET,HEAD")
    }
  }

  s"GET '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    "using the 'plain' and the 'gentrap' run summary files" >> inline {

      new PlainThenGentrapUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 400" in {
            get(endpoint(runId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.UnspecifiedUserId.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint(runId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint(runId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", UserExamples.unverified.id))
          val headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)

          "return status 403" in {
            get(endpoint(runId), params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.Unauthorized.message)
            }
          }
        }

        "when the user authenticates correctly" >> {
          br

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "and queries a run he/she uploaded" >> {
            br

            def userRunId = runId

            "with the default parameter" should {

              "return status 200" in {
                get(endpoint(userRunId), params, headers) { status mustEqual 200 }
              }

              "return a JSON object of the run data" in {
                get(endpoint(userRunId), params, headers) {
                  contentType mustEqual "application/json"
                  body must /("runId" -> userRunId)
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
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return the expected Content-Disposition header" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
                    }
                  }

                  "return the uploaded summary file" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/octet-stream"
                      body mustEqual new String(uploadPayload.content)
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
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return a JSON object of the run data" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/json"
                      body must /("runId" -> userRunId)
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

            "return status 404" in {
              get(endpoint(runId2), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in {
              get(endpoint(runId2), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> CommonMessages.MissingRunId.message)
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
                body must /("message" -> CommonMessages.MissingRunId.message)
              }
            }
          }
        }

        "when an admin authenticates correctly" >> {
          br

          val params = Seq(("userId", UserExamples.admin.id))
          val headers = Map(HeaderApiKey -> UserExamples.admin.activeKey)

          "and queries a run he/she did not upload" >> {
            br

            def userRunId = runId

            "with the default parameter" should {

              "return status 200" in {
                get(endpoint(userRunId), params, headers) { status mustEqual 200 }
              }

              "return a JSON object of the run data" in {
                get(endpoint(userRunId), params, headers) {
                  contentType mustEqual "application/json"
                  body must /("runId" -> userRunId)
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
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return the expected Content-Disposition header" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
                    }
                  }

                  "return the uploaded summary file" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/octet-stream"
                      body mustEqual new String(uploadPayload.content)
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
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return a JSON object of the run data" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/json"
                      body must /("runId" -> userRunId)
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
                body must /("message" -> CommonMessages.MissingRunId.message)
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
            delete(endpoint(runId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(runId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.UnspecifiedUserId.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
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
              body must /("message" -> CommonMessages.UnspecifiedRunId.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
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
            delete(endpoint(runId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            delete(endpoint(runId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonMessages.Unauthenticated.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
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
          def userRunId = runId
          def request = () => delete(endpoint(userRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            priorResponses.last.contentType mustEqual "application/json"
            priorResponses.last.body must /("runId" -> userRunId)
            priorResponses.last.body must /("uploaderId" -> user.id)
            priorResponses.last.body must not /("sampleIds" -> ".+".r)
            priorResponses.last.body must not /("libIds" -> ".+".r)
            priorResponses.last.body must /("nSamples" -> 0)
            priorResponses.last.body must /("nReadGroups" -> 0)
            priorResponses.last.body must /("pipeline" -> "plain")
            priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
                status mustEqual 404
                contentType mustEqual "application/json"
                body must not /("runId" -> ".+".r)
                body must /("message" -> CommonMessages.MissingRunId.message)
              }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Run summary already deleted.")
            }
          }
        }
      }

      "with the default parameters for the 'gentrap' pipeline (v0.4, single sample, single library) should" >> inline {

        new Context.PriorRunUploadClean {
          def pipelineParam = "gentrap"
          def uploadPayload = LumcSummaryExamples.Gentrap.V04.SSampleSRG
          lazy val runId = parse(priorResponse.body).extract[RunRecord].runId.toString

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)
          def userRunId = runId
          def request = () => delete(endpoint(userRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(userRunId), params, headers) {
              priorResponses.last.contentType mustEqual "application/json"
              priorResponses.last.body must /("runId" -> userRunId)
              priorResponses.last.body must /("uploaderId" -> user.id)
              priorResponses.last.body must not /("sampleIds" -> ".+".r)
              priorResponses.last.body must not /("libIds" -> ".+".r)
              priorResponses.last.body must /("annotIds" -> ".+".r)
              priorResponses.last.body must /("refId" -> """\S+""".r)
              priorResponses.last.body must /("nSamples" -> 1)
              priorResponses.last.body must /("nReadGroups" -> 1)
              priorResponses.last.body must /("pipeline" -> "gentrap")
              priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Run summary already deleted.")
            }
          }
        }
      }
    }

    "when an admin authenticates correctly" >> {
      br

      "with the default parameters for the 'plain' pipeline should" >> inline {

        new PlainUploadContext {

          val params = Seq(("userId", UserExamples.admin.id))
          val headers = Map(HeaderApiKey -> UserExamples.admin.activeKey)
          def userRunId = runId
          def request = () => delete(endpoint(userRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            priorResponses.last.contentType mustEqual "application/json"
            priorResponses.last.body must /("runId" -> userRunId)
            priorResponses.last.body must /("uploaderId" -> user.id)
            priorResponses.last.body must not /("sampleIds" -> ".+".r)
            priorResponses.last.body must not /("libIds" -> ".+".r)
            priorResponses.last.body must /("nSamples" -> 0)
            priorResponses.last.body must /("nReadGroups" -> 0)
            priorResponses.last.body must /("pipeline" -> "plain")
            priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Run summary already deleted.")
            }
          }
        }
      }

      "with the default parameters for the 'gentrap' pipeline (v0.4, single sample, single library) should" >> inline {

        new Context.PriorRunUploadClean {
          def pipelineParam = "gentrap"
          def uploadPayload = LumcSummaryExamples.Gentrap.V04.SSampleSRG
          lazy val runId = parse(priorResponse.body).extract[RunRecord].runId.toString

          val params = Seq(("userId", UserExamples.admin.id))
          val headers = Map(HeaderApiKey -> UserExamples.admin.activeKey)
          def userRunId = runId
          def request = () => delete(endpoint(userRunId), params, headers) { response }
          // make priorRequests a Stream so we can use the runId returned from the first request in the second request
          override def priorRequests = super.priorRequests.toStream :+ request

          "return status 200" in {
            priorResponses.last.status mustEqual 200
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(userRunId), params, headers) {
              priorResponses.last.contentType mustEqual "application/json"
              priorResponses.last.body must /("runId" -> userRunId)
              priorResponses.last.body must /("uploaderId" -> user.id)
              priorResponses.last.body must not /("sampleIds" -> ".+".r)
              priorResponses.last.body must not /("libIds" -> ".+".r)
              priorResponses.last.body must /("annotIds" -> ".+".r)
              priorResponses.last.body must /("refId" -> """\S+""".r)
              priorResponses.last.body must /("nSamples" -> 1)
              priorResponses.last.body must /("nReadGroups" -> 1)
              priorResponses.last.body must /("pipeline" -> "gentrap")
              priorResponses.last.body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonMessages.MissingRunId.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 410 again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 410
            }
          }

          "return a JSON object containing the expected message when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> "Run summary already deleted.")
            }
          }
        }
      }
    }
  }
}
