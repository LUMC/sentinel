package nl.lumc.sasc.sentinel.processors

import com.github.fge.jsonschema.core.report.ProcessingMessage
import org.json4s.JValue

import nl.lumc.sasc.sentinel.db.DatabaseProvider
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.getResourceFile
import nl.lumc.sasc.sentinel.validation.IncomingValidator

trait RunProcessor { this: DatabaseProvider =>

  protected def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  protected def getSchemaValidator(schemaUrl: String) = new IncomingValidator(getSchema(schemaUrl))

  type SampleDocument <: BaseSampleDocument

  def validator: IncomingValidator

  def validate(json: JValue): Seq[ProcessingMessage] = validator.validationMessages(json)

  def extractSamples(json: JValue, runId: String,
                     refId: Option[String], annotIds: Option[Seq[String]]): Seq[SampleDocument]
}

