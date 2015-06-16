package nl.lumc.sasc.sentinel.validation

import java.io.ByteArrayInputStream

import org.json4s.JValue
import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.utils.{ RunValidationException, getResourceStream }

/** Trait for validating input JSON with a schema. */
trait ValidationAdapter {

  /** JSON validator. */
  val validator: RunValidator

  /**
   * Creates a JSON validator from a JSON schema stored as a resource.
   *
   * @param schemaResourceUrl URL of the JSON schema.
   * @return a JSON validator.
   */
  def createValidator(schemaResourceUrl: String) = RunValidator(getResourceStream(schemaResourceUrl))

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
          throw new RunValidationException("File is not JSON-formatted.")
      }
    val valResult = validator.validate(json)
    if (!valResult.isSuccess)
      throw new RunValidationException("JSON run summary is invalid.", Option(valResult))
    else json
  }
}
