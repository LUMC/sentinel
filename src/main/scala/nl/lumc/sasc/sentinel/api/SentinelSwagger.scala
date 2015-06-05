package nl.lumc.sasc.sentinel.api

import akka.actor.ActorSystem
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.swagger._

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
  with JacksonSwaggerBase
