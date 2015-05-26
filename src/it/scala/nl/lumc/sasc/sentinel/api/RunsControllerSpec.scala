package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.bson.types.ObjectId
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.{ HeaderApiKey, MaxRunSummarySize, MaxRunSummarySizeMb }
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{ RunDocument, ApiMessage, User }
import nl.lumc.sasc.sentinel.utils.{ getResourceFile, getTimeNow }

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
  implicit val mongo = dbAccess
  val servlet = new RunsController
  addServlet(servlet, "/runs/*")

  "POST '/runs'" >> {
    br

    val endpoint = "/runs"

    "when the pipeline is not specified" should {
      "return status 400 and the correct message" in {
        post(endpoint, Seq(("userId", "devtest"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Pipeline not specified."))
        }
      }
    }

    "when the request body is empty" should {
      "return status 400 and the correct message" in {
        post(endpoint, Seq(("userId", "devtest"), ("pipeline", "unsupported"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Run summary file not specified."))
        }
      }
    }

    "when an invalid pipeline is specified" should {
      "return status 400 and the correct message" in {
        val file = getResourceFile("/schema_examples/unsupported.json")
        post(endpoint, Seq(("userId", "devtest"), ("pipeline", "devtest")), Map("run" -> file)) {
          status mustEqual 400
          apiMessage.collect { case m => m.message } mustEqual Some("Pipeline parameter is invalid.")
        }
      }
    }

    s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB" should {
      "return status 413 and the correct message" in {
        val tooBigFile = createTempFile("tooBig.json")
        post(endpoint, Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> tooBigFile)) {
          status mustEqual 413
          apiMessage mustEqual Some(ApiMessage(s"Run summary exceeds $MaxRunSummarySizeMb MB."))
        } before {
          fillFile(tooBigFile, MaxRunSummarySize + 100)
        } after {
          deleteQuietly(tooBigFile)
        }
      }
    }

    val testUser = User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false, getTimeNow)

    "using the 'unsupported' pipeline type" >> {
      br

      val pipeline = "unsupported"
      val runFile = getResourceFile("/schema_examples/unsupported.json")

      "when the user ID is not specified" should {
        "return status 400 and the correct message" in {
          post(endpoint, Seq(("pipeline", pipeline)), Map("run" -> runFile)) {
            status mustEqual 400
            apiMessage mustEqual Some(ApiMessage("User ID not specified."))
          }
        }
      }

      s"when the user does not provide the $HeaderApiKey header" should {
        "return status 401, the challenge response header, and the correct message" in {
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), Map("run" -> runFile)) {
            status mustEqual 401
            header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
          }
        }
      }

      s"when the provided $HeaderApiKey does not match the one owned by the user" should {
        "return status 401, the challenge response header, and the correct message" in {
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> (testUser.activeKey + "nono"))) {
              status mustEqual 401
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
              apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
            } before {
              servlet.users.addUser(testUser)
            } after { resetDb() }
        }
      }

      "when a user without a verified email address uploads a run summary" should {
        "return status 403 and the correct message" in {
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> testUser.activeKey)) {
              status mustEqual 403
              apiMessage mustEqual Some(ApiMessage("Unauthorized to access resource."))
            } before {
              servlet.users.addUser(testUser.copy(emailVerified = false))
            } after { resetDb() }
        }
      }

      "when a non-JSON file is uploaded" should {
        "return status 400 and the correct message" in {
          val fileMap = Map("run" -> getResourceFile("/schema_examples/not.json"))
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> testUser.activeKey)) {
              status mustEqual 400
              apiMessage must beSome.like { case api => api.message mustEqual "File is not JSON-formatted." }
            } before {
              servlet.users.addUser(testUser)
            } after { resetDb() }
        }
      }

      "when an invalid JSON run summary is uploaded" should {
        "return status 400 and the correct message" in {
          val fileMap = Map("run" -> getResourceFile("/schema_examples/invalid.json"))
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> testUser.activeKey)) {
              status mustEqual 400
              apiMessage must beSome.like { case api => api.message mustEqual "JSON run summary is invalid." }
            } before {
              servlet.users.addUser(testUser)
            } after { resetDb() }
        }
      }

      "when a run summary that passes all validation is uploaded" should {
        "return status 201 and the correct payload" in {
          post(endpoint, Seq(("userId", testUser.id), ("pipeline", pipeline)), Map("run" -> runFile),
            Map(HeaderApiKey -> testUser.activeKey)) {
              status mustEqual 201
              jsonBody.collect { case json => json.extract[RunDocument] } must beSome.like {
                case payload =>
                  payload.runId must not be empty
                  payload.uploader mustEqual "devtest"
                  payload.pipeline mustEqual "unsupported"
                  payload.nSamples mustEqual 0
                  payload.nLibs mustEqual 0
                  payload.annotIds must beNone
                  payload.refId must beNone
              }
            } before {
              servlet.users.addUser(testUser)
            } after { resetDb() }
        }
      }

      "when the same run summary is uploaded more than once by the same user" should {
        "return status 400 and the correct message" in {
          val params = Seq(("userId", testUser.id), ("pipeline", pipeline))
          val headers = Map(HeaderApiKey -> testUser.activeKey)
          val fileMap = Map("run" -> runFile)
          post(endpoint, params, fileMap, headers) {
            status mustEqual 400
            apiMessage must beSome.like { case api => api.message mustEqual "Run summary already uploaded by the user." }
          } before {
            servlet.users.addUser(testUser)
            post(endpoint, params, fileMap, headers) {}
          } after { resetDb() }
        }
      }

      "when the same run summary is uploaded more than once by different users" should {
        "return status 201 and the correct payload" in {
          val testUser2 = testUser.copy(id = "devtest2", _id = new ObjectId)
          val fileMap = Map("run" -> runFile)
          post(endpoint, Seq(("userId", "devtest2"), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> testUser2.activeKey)) {
              status mustEqual 201
              jsonBody.collect { case json => json.extract[RunDocument] } must beSome.like {
                case payload =>
                  payload.runId must not be empty
                  payload.uploader mustEqual "devtest2"
                  payload.pipeline mustEqual "unsupported"
                  payload.nSamples mustEqual 0
                  payload.nLibs mustEqual 0
                  payload.annotIds must beNone
                  payload.refId must beNone
              }
            } before {
              servlet.users.addUser(testUser)
              servlet.users.addUser(testUser2)
              post(endpoint, Seq(("userId", testUser.id), ("pipeline", "unsupported")), fileMap,
                Map(HeaderApiKey -> testUser.activeKey)) {}
            } after { resetDb() }
        }
      }
    }
  }
}
