package nl.lumc.sasc.sentinel.api

import org.scalatra.ScalatraServlet
import org.scalatra.swagger._

/** Servlet for the "/" endpoint */
class RootController(implicit val swagger: Swagger) extends ScalatraServlet {

  /** Root endpoint, which permanently redirects to our documentation */
  get("/") {
    halt(status = 301, headers = Map("Location" -> "/api-docs"))
  }
}
