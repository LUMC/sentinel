package nl.lumc.sasc.sentinel.api

import org.scalatra.test.specs2._

class RootControllerSpec extends MutableScalatraSpec {

  addServlet(new RootController, "/*")

  "GET '/'" should {

    "return status 301" in {
      get("/") {
        status mustEqual 301
      }
    }

    "redirect to /api-docs" in {
      get("/") {
        header("Location") must endWith("/api-docs")
      }
    }

    "have JSON content type" in {
      get("/") {
        header("Content-Type") must contain("application/json")
      }
    }
  }

}
