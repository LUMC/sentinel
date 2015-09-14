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

import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

/**
 * Controller for the `/annotations` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class AnnotationsController(implicit val swagger: Swagger, mongo: MongodbAccessObject)
    extends SentinelServlet
    with FutureSupport { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName = Some("annotations")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Retrieval of annotation file records"

  /** Annotation adapter for connecting to the database. */
  protected val annots = new AnnotationsAdapter { val mongo = self.mongo }

  options("/?") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/:annotId") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  // format: OFF
  val annotIdGetOp = (apiOperation[Seq[AnnotationRecord]]("annotIdGet")
    summary "Retrieves a single full annotation item."
    parameters pathParam[String]("annotId").description("Annotation ID query.")
    responseMessages StringResponseMessage(404, "Annotation ID can not be found."))
  // format: ON

  get("/:annotId", operation(annotIdGetOp)) {
    logger.info(requestLog)
    val errMsg = ApiMessage("Annotation ID can not be found.")
    val annotId = params.getAs[DbId]("annotId").getOrElse(halt(404, errMsg))
    new AsyncResult {
      val is = annots.getAnnotation(annotId)
        .map {
          case None        => NotFound(errMsg)
          case Some(annot) => Ok(annot)
        }
    }
  }

  // format: OFF
  val getOp = (apiOperation[Seq[AnnotationRecord]]("get")
    summary "Retrieves all available annotation items.")
  // format: ON

  get("/", operation(getOp)) {
    logger.info(requestLog)
    new AsyncResult {
      val is = annots.getAnnotations().map(annots => Ok(annots))
    }
  }
}
