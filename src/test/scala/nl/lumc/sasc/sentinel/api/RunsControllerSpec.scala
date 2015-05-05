package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.scalatra.test.specs2._

class RunsControllerSpec extends ScalatraSpec with SentinelSpec { def is = s2"""

    POST / on RunsController must
      return status 400 if user is unspecified                $postRunsUnspecifiedUserStatus
      return the correct message if user is unspecified       $postRunsUnspecifiedUserMessage
      return status 413 if run summary is too large           $postRunsFileTooLargeStatus
      return the correct message if run summary is too large  $postRunsFileTooLargeMessage
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

  addServlet(new RunsController, "/runs/*")

  def postRunsUnspecifiedUserStatus = post("/runs") {
    status mustEqual 400
  }

  def postRunsUnspecifiedUserMessage = post("/runs") {
    bodyMap mustEqual Some(Map("message" -> "User ID not specified."))
  }

  def postRunsFileTooLargeStatus = {
    val tooBigFile = createTempFile("tooBig.json")
    post("/runs", Seq(("userId", "testMan")), Map("run" -> tooBigFile)) {
      status mustEqual 413
    } before {
      fillFile(tooBigFile, 16 * 1024 * 1024 + 100)
    } after {
      deleteQuietly(tooBigFile)
    }
  }

  def postRunsFileTooLargeMessage = {
    val tooBigFile = createTempFile("tooBig.json")
    post("/runs", Seq(("userId", "testMan")), Map("run" -> tooBigFile)) {
      bodyMap mustEqual Some(Map("message" -> "Run summary exceeds 16 MB."))
    } before {
      fillFile(tooBigFile, 16 * 1024 * 1024 + 100)
    } after {
      deleteQuietly(tooBigFile)
    }
  }
}
