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

import org.json4s._
import org.scalatra.{ CorsSupport, ScalatraServlet }
import org.scalatra.json.JacksonJsonSupport

import nl.lumc.sasc.sentinel.utils.SentinelJsonFormats

/** Controller for the `/` endpoint. */
class RootController extends ScalatraServlet
    with CorsSupport
    with JacksonJsonSupport {

  /** JSON formatting used by this endpoint. */
  protected implicit val jsonFormats: Formats = SentinelJsonFormats

  before() {
    contentType = formats("json")
  }

  options("/?") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  /** Root endpoint, which permanently redirects to our documentation. */
  get("/") {
    halt(status = 301, headers = Map("Location" -> "/api-docs"))
  }
}
