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
package nl.lumc.sasc.sentinel.utils

import java.io.ByteArrayInputStream

import nl.lumc.sasc.sentinel.utils.exceptions.JsonValidationException
import org.json4s._
import org.json4s.jackson.JsonMethods._

/** Trait for validating input JSON with a schema. */
trait JsonValidationAdapter {

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
  def createJsonValidator(schemaResourceUrl: String) = JsonValidator(getResourceStream(schemaResourceUrl))

  /**
   * Parses the given byte array into as a JSON file.
   *
   * @param byteContents raw byte contents to parse.
   * @return JSON object representation.
   */
  def parseAndValidate(byteContents: Array[Byte]): JValue = {
    val json =
      try {
        parse(new ByteArrayInputStream(byteContents))
      } catch {
        case exc: Exception =>
          throw new JsonValidationException("File is not JSON-formatted.")
      }
    val valResult = jsonValidator.validate(json)
    if (!valResult.isSuccess)
      throw new JsonValidationException("JSON run summary is invalid.", Option(valResult))
    else json
  }
}
