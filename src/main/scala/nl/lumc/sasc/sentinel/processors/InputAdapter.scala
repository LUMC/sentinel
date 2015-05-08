package nl.lumc.sasc.sentinel.processors

import com.github.fge.jsonschema.core.report.ProcessingMessage
import org.json4s.JValue

import nl.lumc.sasc.sentinel.db.DatabaseProvider
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.getResourceFile
import nl.lumc.sasc.sentinel.validation.RunValidator

trait InputAdapter { this: DatabaseProvider =>

  protected def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  protected def getSchemaValidator(schemaUrl: String) = new RunValidator(getSchema(schemaUrl))

  type SampleDocument <: BaseSampleDocument

  def validator: RunValidator

  def validate(runJson: JValue): Seq[ProcessingMessage] = validator.validationMessages(runJson)

  def extractSamples(runJson: JValue, runId: DbId, refId: DbId, annotIds: Seq[DbId]): Seq[SampleDocument]

  def extractReference(runJson: JValue): Reference

  def extractAnnotations(runJson: JValue): Seq[Annotation]
}

