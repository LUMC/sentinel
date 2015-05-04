package nl.lumc.sasc.sentinel.api

import org.scalatra.test.specs2._

class RunsControllerSpec extends ScalatraSpec with SentinelSpec { def is = s2"""

    POST / on RunsController must
      return status 400 if user is unspecified            $postRunsUnspecifiedUserStatus
      return the correct message if user is unspecified   $postRunsUnspecifiedUserMessage
"""

  implicit val swagger = new SentinelSwagger

  addServlet(new RunsController, "/runs/*")

  def postRunsUnspecifiedUserStatus = post ("/runs") { status mustEqual 400 }

  def postRunsUnspecifiedUserMessage = post ("/runs") {
    bodyMap mustEqual Some(Map("message" -> "User ID not specified."))
  }
}
