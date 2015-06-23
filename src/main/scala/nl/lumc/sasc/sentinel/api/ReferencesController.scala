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

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Controller for the `/references` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class ReferencesController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName: Option[String] = Some("references")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Retrieval of reference sequence records"

  /** Annotation adapter for connecting to the database. */
  protected val refs = new ReferencesAdapter { val mongo = self.mongo }

  options("/?") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/:refId") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  // format: OFF
  val referencesRefIdGetOperation = (apiOperation[Seq[ReferenceRecord]]("referencesRefIdGet")
    summary "Retrieves a single reference record."
    parameters pathParam[String]("refId").description("Reference ID query.")
    responseMessages StringResponseMessage(404, "Reference ID can not be found."))
  // format: ON

  get("/:refId", operation(referencesRefIdGetOperation)) {
    val errMsg = ApiMessage("Reference ID can not be found.")
    val refId = params("refId")
      .getObjectId
      .getOrElse(halt(404, errMsg))
    refs.getReference(refId) match {
      case None      => NotFound(ApiMessage("Reference ID can not be found."))
      case Some(ref) => Ok(ref)
    }
  }

  // format: OFF
  val referencesGetOperation = (apiOperation[ReferenceRecord]("referencesGet")
    summary "Retrieves all available reference records.")
  // format: ON

  get("/", operation(referencesGetOperation)) {
    refs.getReferences()
  }
}
