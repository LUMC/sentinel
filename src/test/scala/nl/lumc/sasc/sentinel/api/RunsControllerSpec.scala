package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.scalatra.test.specs2._
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models.ApiMessage

class RunsControllerSpec extends ScalatraSpec with SentinelServletSpec with Mockito {

  sequential

  def is = """

  POST '/runs' must
    return status 400 with the correct message if user is unspecified               $postRunsUnspecifiedUser
    return status 400 with the correct message if pipeline is unspecified           $postRunsUnspecifiedPipeline
    return status 413 with the correct messageif run summary is too large           $postRunsFileTooLarge
"""

  protected lazy val tempDir = Files.createTempDir()

  def createTempFile(name: String): File = new File(tempDir, name)

  override def stop(): Unit = {
    deleteDirectory(tempDir)
    super.stop()
  }

  protected def fillFile(file: File, size: Long): File = {
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(size)
    raf.length()
    raf.close()
    file
  }

  implicit val swagger = new SentinelSwagger
  implicit val mongo = makeDbAccess

  val servlet = new RunsController
  addServlet(servlet, "/runs/*")

  def postRunsUnspecifiedUser = post("/runs", Seq(("pipeline", "unsupported"))) {
    status mustEqual 400
    apiMessage mustEqual Some(ApiMessage("User ID not specified."))
  }

  def postRunsUnspecifiedPipeline = post("/runs", Seq(("userId", "testMan"))) {
    status mustEqual 400
    apiMessage mustEqual Some(ApiMessage("Pipeline not specified."))
  }

  def postRunsFileTooLarge = {
    val tooBigFile = createTempFile("tooBig.json")
    post("/runs", Seq(("userId", "testMan")), Map("run" -> tooBigFile)) {
      status mustEqual 413
      apiMessage mustEqual Some(ApiMessage("Run summary exceeds 16 MB."))
    } before {
      fillFile(tooBigFile, 16 * 1024 * 1024 + 100)
    } after {
      deleteQuietly(tooBigFile)
    }
  }
}
