package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.{ HeaderApiKey, MaxRunSummarySize, MaxRunSummarySizeMb }
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{ ApiMessage, User }
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
            servlet.users.deleteUser("devtest")
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
            servlet.users.deleteUser("devtest")
          }
      }
    }
  }
}
