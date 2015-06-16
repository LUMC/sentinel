package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

import nl.lumc.sasc.sentinel.utils.SentinelJsonFormats

/** Controller for the `/` endpoint. */
class RootController extends ScalatraServlet with JacksonJsonSupport {

  /** JSON formatting used by this endpoint. */
  protected implicit val jsonFormats: Formats = SentinelJsonFormats

  before() {
    contentType = formats("json")
  }

  /** Root endpoint, which permanently redirects to our documentation. */
  get("/") {
    halt(status = 301, headers = Map("Location" -> "/api-docs"))
  }
}
