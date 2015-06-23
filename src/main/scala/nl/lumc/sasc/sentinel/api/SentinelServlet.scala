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

import org.bson.types.ObjectId
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{ DataType, Model, SwaggerSupport }

import nl.lumc.sasc.sentinel.models.ApiMessage
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, separateObjectIds, splitParam }

/** Base servlet for all Sentinel controllers. */
abstract class SentinelServlet extends ScalatraServlet
    with CorsSupport
    with JacksonJsonSupport
    with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  // FIXME: This is a bit hackish, but scalatra-swagger does not make it clear how to intercept / prevent certain
  //        models from being exposed. Until they have an officially documented way of doing so, we'll stick with this.
  // Intercept ObjectId model creation, to prevent its internal attributes from being exposed in the API
  // We only want to show them as plain strings
  // Also, skip ObjectId creation completely
  override protected def registerModel(model: Model): Unit = {
    if (model.id != "ObjectId") {
      val interceptedProp = model.properties.map {
        case (propName, prop) =>
          val interceptedProp = prop.`type` match {
            case vdt: DataType.ValueDataType if vdt.name == "ObjectId" =>
              prop.copy(`type` = DataType.String)
            case cdt: DataType.ContainerDataType if cdt.typeArg.isDefined && cdt.typeArg.get.name == "ObjectId" =>
              cdt.name match {
                case "List"    => prop.copy(`type` = DataType.GenList(DataType.String))
                case "Set"     => prop.copy(`type` = DataType.GenSet(DataType.String))
                case otherwise => prop
              }
            case otherwise => prop
          }
          (propName, interceptedProp)
      }
      val newModel =
        if (model.id == "RunDocument")
          model.copy(properties = interceptedProp.filter {
            case (propName, prop) => propName != "sampleIds" || propName != "samples"
          })
        else
          model.copy(properties = interceptedProp)
      super.registerModel(newModel)
    }
  }

  /** JSON format for Sentinel responses */
  protected implicit val jsonFormats: Formats = SentinelJsonFormats

  // NOTE: Java's MongoDB driver parses all MapReduce number results to Double, so we have to resort to this.
  /** Transforms MongoDB mapReduce nDataPoints attribute to the proper type */
  protected def transformMapReduceResult(results: Any): JValue =
    Extraction.decompose(results)
      .transformField { case JField("nDataPoints", JDouble(n)) => ("nDataPoints", JInt(n.toLong)) }

  /** Creates ObjectIds from a sequence of strings. */
  protected def getObjectIds(strs: Seq[String], msg: Option[ApiMessage] = None): Seq[ObjectId] = {
    val (validIds, invalidIds) = separateObjectIds(strs)
    if (invalidIds.nonEmpty) msg match {
      case None    => halt(400)
      case Some(m) => halt(400, m.copy(data = Map("invalid" -> invalidIds)))
    }
    else validIds
  }

  /** Helper function for creating run object IDs with the appropriate failure message. */
  protected def getRunObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid run ID(s) provided.")))

  /** Helper function for creating reference object IDs with the appropriate failure message. */
  protected def getRefObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid reference ID(s) provided.")))

  /** Helper function for creating annotation object IDs with the appropriate failure message. */
  protected def getAnnotObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid annotation ID(s) provided.")))

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  after() {
    // Disable cookie header for now
    response.setHeader("Set-Cookie", null)
    // Remove nonstandard field added automatically by Scalatra
    response.setHeader("REMOTE_USER", null)
  }
}
