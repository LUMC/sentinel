package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

/** Servlet for the "/" endpoint */
class RootController extends ScalatraServlet with JacksonJsonSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  /** Root endpoint, which permanently redirects to our documentation */
  get("/") {
    halt(status = 301, headers = Map("Location" -> "/api-docs"))
  }
}
