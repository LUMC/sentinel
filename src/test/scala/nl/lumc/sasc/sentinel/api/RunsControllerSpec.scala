package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.ApiMessage

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

    "when the submitted run summary exceeds 16 MB" should {
      "return status 400 and the correct message" in {
        val tooBigFile = createTempFile("tooBig.json")
        post("/runs", Seq(("userId", "devtest"), ("pipeline", "unsupported")), Map("run" -> tooBigFile)) {
          status mustEqual 413
          apiMessage mustEqual Some(ApiMessage("Run summary exceeds 16 MB."))
        } before {
          fillFile(tooBigFile, 16 * 1024 * 1024 + 100)
        } after {
          deleteQuietly(tooBigFile)
        }
      }
    }
  }
}
