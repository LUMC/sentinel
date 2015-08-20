/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
class ApiSpecsController(implicit protected val system: ActorSystem, val swagger: Swagger) extends ScalatraServlet
  with JacksonSwaggerBase