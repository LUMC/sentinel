package nl.lumc.sasc.sentinel.api

import akka.actor.ActorSystem
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ApiInfo, JacksonSwaggerBase, Swagger}

import nl.lumc.sasc.sentinel.CurrentApiVersion

class SentinelSwagger extends Swagger(apiInfo = SentinelSwagger.apiInfo, apiVersion = CurrentApiVersion,
                                      swaggerVersion = Swagger.SpecVersion)

object SentinelSwagger {
  val apiInfo = ApiInfo(
    """Sentinel API""",
    """API for the Sentinel NGS QC database.""",
    """http://sasc.lumc.nl""",
    """sasc@lumc.nl""",
    """All rights reserved""",
    """http://apache.org/licenses/LICENSE-2.0.html""")
}

class ResourcesApp(implicit protected val system: ActorSystem, val swagger: SentinelSwagger) extends ScalatraServlet
  with JacksonSwaggerBase {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  before() {
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  protected def buildFullUrl(path: String) = if (path.startsWith("http")) path else {
    val port = request.getServerPort
    val h = request.getServerName
    val prot = if (port == 443) "https" else "http"
    val (proto, host) = if (port != 80 && port != 443) ("http", h+":"+port.toString) else (prot, h)
    "%s://%s%s%s".format(
      proto,
      host,
      request.getContextPath,
      path)
  }
}
