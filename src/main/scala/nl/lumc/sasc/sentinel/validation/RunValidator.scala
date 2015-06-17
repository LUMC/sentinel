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
package nl.lumc.sasc.sentinel.validation

import scala.collection.JavaConverters._

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.core.report.{ ProcessingMessage, ProcessingReport }
import com.github.fge.jsonschema.main._
import org.json4s._
import org.json4s.jackson.JsonMethods._

/**
 * Validator for incoming JSON payloads.
 *
 * @param rawSchema JSON schema to validate against, as a JValue object.
 */
class RunValidator(rawSchema: JValue) {

  import nl.lumc.sasc.sentinel.validation.RunValidator._

  /** Alternative constructor for creating a validator from any valid JsonInput object. */
  def this(in: JsonInput) {
    this(parse(in))
  }

  /** JsonSchema object which provides the validation functions. */
  protected val schema: JsonSchema = factory.getJsonSchema(rawSchema)

  /**
   * Validates the given JSON.
   *
   * @param instance JSON instance to validate.
   * @return ProcessingReport instance.
   */
  def validate(instance: JValue): ProcessingReport = schema.validate(instance)

  /**
   * Validates the given JSON and captures any validation messages in a container.
   *
   * @param instance JSON instance to validate.
   * @return a sequence of ProcessingMessages. If the validation succeeds without any errors or warnings,
   *         the container will be empty.
   */
  def validationMessages(instance: JValue): Seq[ProcessingMessage] = validate(instance)
    .iterator().asScala.toSeq

}

object RunValidator {

  import scala.language.implicitConversions

  /** Constructor for new [[RunValidator]] objects. */
  def apply(in: JsonInput) = new RunValidator(in)

  /** Implicit conversion from a JValue object to a JsonNode object; used internally by the validator. */
  implicit def toJsonNode(jv: JValue): JsonNode = asJsonNode(jv)

  /** Factory for JSON schemas */
  protected val factory: JsonSchemaFactory = JsonSchemaFactory.byDefault()
}
