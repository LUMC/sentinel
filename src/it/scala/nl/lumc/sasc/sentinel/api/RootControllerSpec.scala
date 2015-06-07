package nl.lumc.sasc.sentinel.api

import akka.actor.ActorSystem
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.CurrentApiVersion

class RootControllerSpec extends SentinelServletSpec with Mockito {

  implicit val system = mock[ActorSystem]
  implicit val swagger = new SentinelSwagger
  addServlet(new RootController, s"/*")
  addServlet(new ResourcesApp, "/api-docs/*")

  s"GET '/' should" >> inline {

    val endpoint = "/"

    new Context.PriorRequests {

      def request = () => get(endpoint) { response }
      def priorRequests = Seq(request)

      "return status 301" in {
        priorResponse.status mustEqual 301
      }

      "redirect to /api-docs" in {
        priorResponse.header("Location") must endWith("/api-docs")
      }

      "return an empty body" in {
        priorResponse.body must beEmpty
      }
    }
  }

  "GET '/api-docs' should" >> inline {

    val endpoint = "/api-docs"

    new Context.PriorRequests {

      def request = () => get(endpoint) { response }
      def priorRequests = Seq(request)

      "return status 200" in {
        priorResponse.status mustEqual 200
      }

      "return a JSON object containing the API version" in {
        priorResponse.contentType mustEqual "application/json"
        priorResponse.body must /("apiVersion" -> CurrentApiVersion)
      }
    }
  }
}
