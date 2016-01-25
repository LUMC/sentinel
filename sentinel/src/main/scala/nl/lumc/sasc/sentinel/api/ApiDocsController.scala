/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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

import java.util.Properties

import org.scalatra._

import nl.lumc.sasc.sentinel.utils.getResourceStream

/** Controller for the `/api-docs` endpoint. */
class ApiDocsController extends ScalatraServlet {

  /** Helper method to get requested static resource path. */
  private def getResourcePath =
    multiParams("splat").headOption match {
      case None                 => request.getServletPath + "/index.html"
      case Some(s) if s.isEmpty => request.getServletPath + "/index.html"
      case Some(splatPath)      => request.getServletPath + s"/$splatPath"
    }

  /** Endpoint for live Swagger documentation. */
  get("/*") {
    val resourcePath = getResourcePath
    getResourceStream(resourcePath) match {

      case Some(inputStream) =>
        contentType = ApiDocsController.resolveContentType(resourcePath)
        response.setHeader("Cache-Control", "public")
        Ok(inputStream)

      case None =>
        contentType = "application/json"
        NotFound("{\"message\":\"Requested resource not found.\"}")
    }
  }
}

object ApiDocsController {

  /** MIME properties for setting mime type in HTTP responses. */
  private val properties: Properties = new Properties()
  private val mimeProps = getResourceStream("/mime.properties")
  require(mimeProps.isDefined)
  mimeProps.foreach(properties.load)

  /** Helper method for getting file extension. */
  private def suffix(path: String): String = path.reverse.takeWhile(_ != '.').reverse

  /** Given a resource path, return its MIME-type. */
  def resolveContentType(resourcePath: String) = Option(properties.get(suffix(resourcePath))) match {
    case Some(ct) => ct.toString
    case None     => "text/plain"
  }
}
