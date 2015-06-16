package nl.lumc.sasc.sentinel.api

import akka.actor.ActorSystem
import org.scalatra.ScalatraServlet
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.CurrentApiVersion

/** Swagger specification container. */
class SentinelSwagger extends Swagger(apiInfo = SentinelSwagger.apiInfo, apiVersion = CurrentApiVersion,
  swaggerVersion = Swagger.SpecVersion)

/** General API info. */
object SentinelSwagger {
  val apiInfo = ApiInfo(
    title = """Sentinel API""",
    description = """Sentinel is a database of various next-generation sequencing metrics.""",
    termsOfServiceUrl = """http://sasc.lumc.nl""",
    contact = """sasc@lumc.nl""",
    license = """All rights reserved.""",
    licenseUrl = """http://apache.org/licenses/LICENSE-2.0.html""")
}

/** Controller for auto-generated Swagger specification. */
class ResourcesApp(implicit protected val system: ActorSystem, val swagger: SentinelSwagger) extends ScalatraServlet
  with JacksonSwaggerBase
