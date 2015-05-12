package nl.lumc.sasc.sentinel.validation

import com.github.fge.jsonschema.core.report.ProcessingMessage
import org.json4s.JValue

import nl.lumc.sasc.sentinel.utils.getResourceFile

trait ValidationAdapter {

  def validator: RunValidator

  protected def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  protected def getSchemaValidator(schemaUrl: String) = new RunValidator(getSchema(schemaUrl))

  def validate(runJson: JValue): Seq[ProcessingMessage] = validator.validationMessages(runJson)
}
