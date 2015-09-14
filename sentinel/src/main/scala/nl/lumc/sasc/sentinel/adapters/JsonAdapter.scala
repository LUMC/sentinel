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
import scala.util.{ Failure, Success, Try }

import org.json4s._
import org.json4s.jackson.JsonMethods.parse

import nl.lumc.sasc.sentinel.utils.{ getResourceStream, JsonValidator }
import nl.lumc.sasc.sentinel.utils.exceptions.JsonValidationException

/** Trait for parsing JSON. */
trait JsonAdapter {

  /**
   * Parses the given byte array into JSON.
   *
   * @param byteContents Raw bytes to parse.
   * @return JValue object.
   */
  def parseJson(byteContents: Array[Byte]): JValue = parse(new ByteArrayInputStream(byteContents))
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
   * Parses the given byte array into as a JSON file.
   *
   * @param byteContents raw byte contents to parse.
   * @return JSON object representation.
   */
  def parseAndValidateJson(byteContents: Array[Byte]): JValue = {
    val json = Try(parseJson(byteContents)) match {
      case Success(jv) => jv
      case Failure(_)  => throw new JsonValidationException("File is not JSON-formatted.")
    }
    val valResult = jsonValidator.validate(json)
    if (!valResult.isSuccess) throw new JsonValidationException("JSON is invalid.", Option(valResult))
    else json
  }
}
