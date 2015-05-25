package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
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

    "when the user is not specified" should {
      "return status 400 and the correct message" in {
        post("/runs", Seq(("pipeline", "unsupported"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("User ID not specified."))
        }
      }
    }

    "when the pipeline is not specified" should {
      "return status 400 and the correct message" in {
        post("/runs", Seq(("userId", "devtest"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Pipeline not specified."))
        }
      }
    }

    "when the request body is empty" should {
      "return status 400 and the correct message" in {
        post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported"))) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Run summary file not specified."))
        }
      }
    }

    "when an invalid pipeline is specified" should {
      "return status 400 and the correct message" in {
        val file = getResourceFile("/schema_examples/unsupported.json")
        post("/runs", Seq(("userId", "devtest"), ("pipeline", "devtest")), Map("run" -> file)) {
          status mustEqual 400
          apiMessage.collect { case m => m.message } mustEqual Some("Pipeline parameter is invalid.")
        }
      }
    }

    s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB" should {
      "return status 400 and the correct message" in {
        val tooBigFile = createTempFile("tooBig.json")
        post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> tooBigFile)) {
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
      s"when the user does not provide the $HeaderApiKey header" should {
        "return status 401, the challenge response header, and the correct message" in {
          val file = getResourceFile("/schema_examples/unsupported.json")
          post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> file)) {
            status mustEqual 401
            header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
          }
        }
      }

      s"when the provided $HeaderApiKey does not match the one owned by the user" should {
        "return status 401, the challenge response header, and the correct message" in {
          val file = getResourceFile("/schema_examples/unsupported.json")
          post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> file),
            Map(HeaderApiKey -> "key")) {
              status mustEqual 401
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
              apiMessage mustEqual Some(ApiMessage("Authentication required to access resource."))
            } before {
              servlet.users.addUser(User("devtest", "d@d.id", "pwd", "diffKey", emailVerified = true, isAdmin = false,
                getTimeNow))
            } after {
              resetDb()
            }
        }
      }

      "when a user without a verified email address uploads a run summary" should {
        "return status 403 and the correct message" in {
          val file = getResourceFile("/schema_examples/unsupported.json")
          post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> file),
            Map(HeaderApiKey -> "key")) {
              status mustEqual 403
              apiMessage mustEqual Some(ApiMessage("Unauthorized to access resource."))
            } before {
              servlet.users.addUser(User("devtest", "d@d.id", "pwd", "key", emailVerified = false, isAdmin = false,
                getTimeNow))
            } after {
              resetDb()
            }
        }
      }

      "when a run summary that passes all validation is uploaded" should {
        "return status 201 and the correct payload" in {
          val file = getResourceFile("/schema_examples/unsupported.json")
          post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> file),
            Map(HeaderApiKey -> "key")) {
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
              servlet.users.addUser(User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false,
                getTimeNow))
            } after { resetDb() }
        }
      }

      "when the same run summary is uploaded more than once by the same user" should {
        "return status 400 and the correct message" in {
          val params = Seq(("userId", "devtest"), ("pipeline", "unsupported"))
          val headers = Map(HeaderApiKey -> "key")
          val fileMap = Map("run" -> getResourceFile("/schema_examples/unsupported.json"))
          post("/runs", params, fileMap, headers) {
            status mustEqual 400
            apiMessage must beSome.like { case api => api.message mustEqual "Run summary already uploaded by the user." }
          } before {
            servlet.users.addUser(User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false,
              getTimeNow))
            post("/runs", params, fileMap, headers) {}
          } after { resetDb() }
        }
      }

      "when the same run summary is uploaded more than once by different users" should {
        "return status 201 and the correct payload" in {
          val headers = Map(HeaderApiKey -> "key")
          val fileMap = Map("run" -> getResourceFile("/schema_examples/unsupported.json"))
          post("/runs", Seq(("userId", "devtest2"), ("pipeline", "unsupported")), fileMap, headers) {
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
            servlet.users.addUser(User("devtest1", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false,
              getTimeNow))
            servlet.users.addUser(User("devtest2", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false,
              getTimeNow))
            post("/runs", Seq(("userId", "devtest1"), ("pipeline", "unsupported")), fileMap, headers) {}
          } after { resetDb() }
        }
      }
    }
  }
}
