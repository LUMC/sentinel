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
package nl.lumc.sasc.sentinel.adapters

import java.io.ByteArrayInputStream
import scala.util.Try
import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.jackson.JsonMethods.parse
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.{ ApiPayload, CommonMessages }, CommonMessages._
import nl.lumc.sasc.sentinel.utils.{ getResourceStream, JsonValidator, SentinelJsonFormats }

/** Trait for parsing JSON. */
trait JsonAdapter {

  /** JSON format for Sentinel. */
  implicit val formats = SentinelJsonFormats

  /**
   * Parses the given byte array into JSON.
   *
   * @param contents Raw bytes to parse.
   * @return JValue object.
   */
  def extractJson(contents: Array[Byte]): ApiPayload \/ JValue =
    Try(parse(new ByteArrayInputStream(contents))) match {
      case scala.util.Failure(_)  => JsonValidationError("File is not JSON.").left
      case scala.util.Success(jv) => jv.right
    }
}

/** Trait for validating input JSON with a schema. */
trait JsonValidationAdapter extends JsonAdapter {

  /** Resource URL for JSON schema file. */
  def jsonSchemaUrl: String

  /** JSON validator. */
  lazy val jsonValidator: JsonValidator = createJsonValidator(jsonSchemaUrl)

  /**
   * Creates a JSON validator from a JSON schema stored as a resource.
   *
   * @param schemaResourceUrl URL of the JSON schema.
   * @return a JSON validator.
   */
  protected def createJsonValidator(schemaResourceUrl: String) = JsonValidator(getResourceStream(schemaResourceUrl))

  /**
   * Checks whether a parsed JSON value fulfills the given schema or not.
   *
   * @param json Input JSON value.
   * @return Validation result.
   */
  def isValid(validator: JsonValidator)(json: JValue): ApiPayload \/ JValue = {
    val errMsgs = validator.validate(json).iterator().map(_.toString).toList
    if (errMsgs.isEmpty) json.right
    else JsonValidationError(errMsgs).left
  }

  /**
   * Parses the given byte array into as a JSON file.
   *
   * @param contents raw byte contents to parse.
   * @return JSON object representation.
   */
  override def extractJson(contents: Array[Byte]): ApiPayload \/ JValue = for {
    json <- super.extractJson(contents)
    validJson <- isValid(jsonValidator)(json)
  } yield validJson
}
