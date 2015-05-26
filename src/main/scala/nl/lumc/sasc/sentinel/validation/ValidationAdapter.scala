package nl.lumc.sasc.sentinel.validation

import java.io.ByteArrayInputStream

import org.json4s.JValue
import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.utils.{ RunValidationException, getResourceFile }

trait ValidationAdapter {

  val validator: RunValidator

  def createValidator(schemaResourceUrl: String) = new RunValidator(getResourceFile(schemaResourceUrl))

  def parseAndValidate(byteContents: Array[Byte]): JValue = {
    val json =
      try {
        parse(new ByteArrayInputStream(byteContents))
      } catch {
        case exc: Exception =>
          throw new RunValidationException("File is not JSON-formatted.", Seq())
      }
    val msgs = validator.validationMessages(json)
    if (msgs.nonEmpty) throw new RunValidationException("JSON run summary is invalid.", msgs)
    else json
  }
}
