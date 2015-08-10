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

import javax.servlet.http.HttpServletRequest
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

import org.bson.types.ObjectId
import org.json4s._
import org.scalatra.{ CorsSupport, FutureSupport, NotFound, ScalatraServlet }
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{ DataType, Model, SwaggerSupport }
import org.scalatra.util.conversion.TypeConverter
import org.slf4j.LoggerFactory

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.models.{ ApiMessage, BaseRunRecord, CommonMessages }
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, separateObjectIds, tryMakeObjectId }

/** Base servlet for all Sentinel controllers. */
abstract class SentinelServlet extends ScalatraServlet
    with CorsSupport
    with FutureSupport
    with JacksonJsonSupport
    with SwaggerSupport {

  /** Type of database ID. */
  type DbId = ObjectId

  /** Logger instance. */
  protected val logger = LoggerFactory.getLogger(getClass)

  /** Default log string and helper methods for log string. */
  protected def reqUri(implicit req: HttpServletRequest): String = req.getRequestURI
  protected def reqMethod(implicit req: HttpServletRequest): String = req.getMethod
  protected def reqAddress(implicit req: HttpServletRequest): String = req.getRemoteAddr
  protected def requestLog: String = s"$reqAddress $reqMethod $reqUri"

  /** Default execution context. */
  implicit protected def executor: ExecutionContext = scala.concurrent.ExecutionContext.global

  /** Delimiter for multi-valued URL parameter. */
  protected val multiParamDelimiter: String = ","

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

            case cdt: DataType.ContainerDataType => cdt.typeArg match {

              case Some(dt) if dt.name == "ObjectId" && cdt.name == "List" =>
                prop.copy(`type` = DataType.GenList(DataType.String))

              case Some(dt) if dt.name == "ObjectId" && cdt.name == "Set" =>
                prop.copy(`type` = DataType.GenSet(DataType.String))

              case otherwise => prop
            }

            case otherwise => prop
          }
          (propName, interceptedProp)
      }
      val newModel =
        if (model.id == "RunRecord")
          model.copy(properties = interceptedProp.filter {
            case (propName, prop) => !BaseRunRecord.hiddenAttributes.contains(propName)
          })
        else
          model.copy(properties = interceptedProp)
      super.registerModel(newModel)
    }
  }

  /** JSON format for Sentinel responses */
  protected implicit val jsonFormats: Formats = SentinelJsonFormats

  /** Implicit conversion from URL parameter to accumulation level enum. */
  protected implicit val stringToAccLevel: TypeConverter[String, AccLevel.Value] =
    new TypeConverter[String, AccLevel.Value] {
      def apply(str: String): Option[AccLevel.Value] =
        Try(AccLevel.withName(str)) match {
          case Success(s) => Option(s)
          // Halt when the supplied string parameter is not convertible to enum. This allows us to return a useful
          // error message instead of just silently failing.
          case Failure(_) => halt(400, CommonMessages.InvalidAccLevel)
        }
    }

  /** Implicit conversion from URL parameter to library type enum. */
  protected implicit val stringToLibType: TypeConverter[String, LibType.Value] =
    new TypeConverter[String, LibType.Value] {
      def apply(str: String): Option[LibType.Value] =
        Try(LibType.withName(str)) match {
          case Success(s) => Option(s)
          case Failure(_) => halt(400, CommonMessages.InvalidLibType)
        }
    }

  /** Implicit conversion from URL parameter to sequencing QC phase enum. */
  protected implicit val stringToSeqQcPhase: TypeConverter[String, SeqQcPhase.Value] =
    new TypeConverter[String, SeqQcPhase.Value] {
      def apply(str: String): Option[SeqQcPhase.Value] =
        Try(SeqQcPhase.withName(str)) match {
          case Success(s) => Option(s)
          case Failure(_) => halt(400, CommonMessages.InvalidSeqQcPhase)
        }
    }

  /** Implicit conversion from URL parameter to a sequence of strings. */
  protected implicit val stringToSeqString: TypeConverter[String, Seq[String]] =
    new TypeConverter[String, Seq[String]] {
      def apply(str: String): Option[Seq[String]] =
        Option(str.split(multiParamDelimiter).toSeq)
    }

  /** Implicit conversion from URL parameter to a sequence of database IDs. */
  protected implicit val stringToSeqDbId: TypeConverter[String, Seq[DbId]] =
    new TypeConverter[String, Seq[DbId]] {
      def apply(str: String): Option[Seq[DbId]] = {
        val (validIds, invalidIds) = separateObjectIds(str.split(multiParamDelimiter).toSeq)
        if (invalidIds.nonEmpty)
          halt(400, CommonMessages.InvalidDbId.copy(hint = invalidIds))
        else Option(validIds)
      }
    }

  /** Implicit conversion from URL parameter to a sequence of database IDs. */
  protected implicit val stringToDbId: TypeConverter[String, DbId] =
    new TypeConverter[String, DbId] {
      def apply(str: String): Option[DbId] = tryMakeObjectId(str).toOption
    }

  // NOTE: Java's MongoDB driver parses all MapReduce number results to Double, so we have to resort to this.
  /** Transforms MongoDB mapReduce nDataPoints attribute to the proper type */
  protected def transformMapReduceResult(results: Any): JValue =
    Extraction.decompose(results)
      .transformField { case JField("nDataPoints", JDouble(n)) => ("nDataPoints", JInt(n.toLong)) }

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

  notFound {
    NotFound(ApiMessage("Requested resource not found."))
  }
}
