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
 * @param rawSchema JSON schema to validate against, as a [[JValue]] object.
 */
class RunValidator(rawSchema: JValue) {

  import nl.lumc.sasc.sentinel.validation.RunValidator._

  /** Alternative constructor for creating a validator from any valid [[JsonInput]] object. */
  def this(in: JsonInput) {
    this(parse(in))
  }

  /** [[JsonSchema]] object which provides the validation functions. */
  protected val schema: JsonSchema = factory.getJsonSchema(rawSchema)

  /**
   * Validates the given JSON.
   *
   * @param instance JSON instance to validate.
   * @return [[ProcessingReport]] instance.
   */
  def validate(instance: JValue): ProcessingReport = schema.validate(instance)

  /**
   * Validates the given JSON and captures any validation messages in a container.
   *
   * @param instance JSON instance to validate.
   * @return [[Seq]] containing [[ProcessingMessage]]. If the validation succeeds without any errors or warnings,
   *         the container will be empty.
   */
  def validationMessages(instance: JValue): Seq[ProcessingMessage] = validate(instance)
    .iterator().asScala.toSeq

}

object RunValidator {

  import scala.language.implicitConversions

  /** Constructor for new [[RunValidator]] objects. */
  def apply(in: JsonInput) = new RunValidator(in)

  /** Implicit conversion from a [[JValue]] object to a [[JsonNode]] object; used internally by the validator. */
  implicit def toJsonNode(jv: JValue): JsonNode = asJsonNode(jv)

  /** Factory for JSON schemas */
  protected val factory: JsonSchemaFactory = JsonSchemaFactory.byDefault()
}
