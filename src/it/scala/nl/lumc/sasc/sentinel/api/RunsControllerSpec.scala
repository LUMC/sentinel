package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.bson.types.ObjectId
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.{ HeaderApiKey, MaxRunSummarySize, MaxRunSummarySizeMb }
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{CommonErrors, RunDocument, ApiMessage}
import nl.lumc.sasc.sentinel.utils.getResourceFile

class RunsControllerSpec extends SentinelServletSpec with Mockito {

  sequential

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

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val servlet = new RunsController
  val endpoint = "/runs"
  addServlet(servlet, s"$endpoint/*")

  s"POST '$endpoint'" >> {
    br

    "when the pipeline is not specified" should {
      "return status 400 and the correct message" in {
        post(endpoint, Seq(("userId", Users.avg.id))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Pipeline not specified."))
        }
      }
    }

    "when the request body is empty" should {
      "return status 400 and the correct message" in {
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "unsupported"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Run summary file not specified."))
        }
      }
    }

    "when an invalid pipeline is specified" should {
      "return status 400 and the correct message" in {
        val file = getResourceFile("/schema_examples/unsupported.json")
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "devtest")), Map("run" -> file)) {
          status mustEqual 400
          apiMessage.collect { case m => m.message } mustEqual Some("Pipeline parameter is invalid.")
        }
      }
    }

    s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB" should {
      "return status 413 and the correct message" in {
        val tooBigFile = createTempFile("tooBig.json")
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "unsupported")), Map("run" -> tooBigFile)) {
          status mustEqual 413
          apiMessage mustEqual Some(ApiMessage(s"Run summary exceeds $MaxRunSummarySizeMb MB."))
        } before {
          fillFile(tooBigFile, MaxRunSummarySize + 100)
        } after {
          deleteQuietly(tooBigFile)
        }
      }
    }

    "using the 'unsupported' pipeline type" >> {
      br

      val pipeline = "unsupported"
      lazy val runFile = getResourceFile("/schema_examples/unsupported.json")

      "when the user ID is not specified" should {
        "return status 400 and the correct message" in {
          post(endpoint, Seq(("pipeline", pipeline)), Map("run" -> runFile)) {
            status mustEqual 400
            apiMessage mustEqual Some(ApiMessage("User ID not specified."))
          }
        }
      }

      s"when the user does not provide the $HeaderApiKey header" should {
        "return status 401, the challenge response header, and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runFile)) {
            status mustEqual 401
            header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
          }
        }
      }

      s"when the provided $HeaderApiKey does not match the one owned by the user" should {
        "return status 401, the challenge response header, and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> (user.activeKey + "nono"))) {
              status mustEqual 401
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
              apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
            }
        }
      }

      "when a user without a verified email address uploads a run summary" should {
        "return status 403 and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", Users.unverified.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> Users.unverified.activeKey)) {
              status mustEqual 403
              apiMessage mustEqual Some(ApiMessage("Unauthorized to access resource."))
            }
        }
      }

      "when a non-JSON file is uploaded" should {
        "return status 400 and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          val fileMap = Map("run" -> getResourceFile("/schema_examples/not.json"))
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 400
              apiMessage must beSome.like { case api => api.message mustEqual "File is not JSON-formatted." }
            }
        }
      }

      "when an invalid JSON run summary is uploaded" should {
        "return status 400 and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          val fileMap = Map("run" -> getResourceFile("/schema_examples/invalid.json"))
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 400
              apiMessage must beSome.like { case api => api.message mustEqual "JSON run summary is invalid." }
            }
        }
      }

      "when a run summary that passes all validation is uploaded" should {
        "return status 201 and the correct payload" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 201
              jsonBody.collect { case json => json.extract[RunDocument] } must beSome.like {
                case payload =>
                  payload.runId must not be empty
                  payload.uploaderId mustEqual user.id
                  payload.pipeline mustEqual "unsupported"
                  payload.nSamples mustEqual 0
                  payload.nLibs mustEqual 0
                  payload.annotIds must beNone
                  payload.refId must beNone
              }
            }
        }
      }

      "when the same run summary is uploaded more than once by the same user" should {
        "return status 400 and the correct message" in new ExampleContext.CleanDatabaseWithUser {
          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> user.activeKey)
          def fileMap = Map("run" -> runFile)
          override def before = {
            super.before
            post(endpoint, params, fileMap, headers) {}
          }
          post(endpoint, params, fileMap, headers) {
            status mustEqual 400
            apiMessage must beSome.like { case api => api.message mustEqual "Run summary already uploaded by the user." }
          }
        }
      }

      "when the same run summary is uploaded more than once by different users" should {
        "return status 201 and the correct payload" in new ExampleContext.CleanDatabaseWithUser {
          def fileMap = Map("run" -> runFile)
          override def before = {
            super.before
            post(endpoint, Seq(("userId", Users.avg2.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> Users.avg2.activeKey)) {}
          }
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 201
              jsonBody.collect { case json => json.extract[RunDocument] } must beSome.like {
                case payload =>
                  payload.runId must not be empty
                  payload.uploaderId mustEqual user.id
                  payload.pipeline mustEqual "unsupported"
                  payload.nSamples mustEqual 0
                  payload.nLibs mustEqual 0
                  payload.annotIds must beNone
                  payload.refId must beNone
              }
            }
        }
      }
    }
  }

  s"GET '$endpoint'" >> {
    br

    "when the database is empty" >> inline {

      new SpecContext.CleanDatabaseWithUser {

        "when done by an unverified user with default parameters" should {

          val params = Seq(("userId", Users.unverified.id))
          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return the correct message" in {
            get(endpoint, params, headers) {
              body must /("message" -> CommonErrors.Unauthorized.message)
            }
          }
        }

        "when done by an unauthenticated user with default parameters" should {

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

          "return the correct message" in {
            get(endpoint, params, headers) {
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }
        }

        "when done by an authenticated user with default parameters" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 200" in {
            get(endpoint, params, headers) { status mustEqual 200 }
          }

          "return an empty JSON list" in {
            get(endpoint, params, headers) { jsonBody must haveSize(0) }
          }
        }
      }
    }

    class UnsupportedUploadContext extends SpecContext.AfterRunUpload {
      override def uploadEndpoint = endpoint
      def pipeline = "unsupported"
      lazy val runFile = getResourceFile("/schema_examples/unsupported.json")
    }

    "using an unsupported run summary" >> inline {

      new UnsupportedUploadContext {

        "when done by an unverified user with default parameters" should {

          val params = Seq(("userId", Users.unverified.id))
          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return the correct message" in {
            get(endpoint, params, headers) {
              body must /("message" -> CommonErrors.Unauthorized.message)
            }
          }
        }

        "when done by an unauthenticated user with default parameters" should {

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

          "return the correct message" in {
            get(endpoint, params, headers) {
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }
        }

        "when done by an authenticated user with default parameters" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 200" in {
            get(endpoint, params, headers) { status mustEqual 200 }
          }

          "return a JSON list containing a single run object with the correct payload" in {
            get(endpoint, params, headers) {
              jsonBody must haveSize(1)
              body must /#(0) */("runId" -> ".+".r)
              body must /#(0) */("uploaderId" -> user.id)
              body must /#(0) */("pipeline" -> "unsupported")
              body must /#(0) */("nSamples" -> 0)
              body must /#(0) */("nLibs" -> 0)
              body must not /# 0 */ "refId"
              body must not /# 0 */ "annotIds"
            }
          }
        }
      }
    }
  }

}
