package nl.lumc.sasc.sentinel.api

import org.scalatra.test.specs2._

class RootControllerSpec extends ScalatraSpec { def is = s2"""

  GET / on RootController must
    return status 301           $getRoot301
    redirect to /api-docs       $getRootRedirect
    have JSON content type      $getRootContentType
"""

  addServlet(new RootController, "/*")

  def getRoot301 = get("/") {
    status mustEqual 301
  }

  def getRootRedirect  = get("/") {
    header("Location") must endWith("/api-docs")
  }

  def getRootContentType = get("/") {
    header("Content-Type") must contain("application/json")
  }
}
