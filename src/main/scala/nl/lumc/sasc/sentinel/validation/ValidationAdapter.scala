package nl.lumc.sasc.sentinel.validation

import scala.util.{ Success, Try }

import com.github.fge.jsonschema.core.report.ProcessingMessage
import org.scalatra.servlet.FileItem
import org.json4s.JValue

import nl.lumc.sasc.sentinel.processors.SentinelProcessor
import nl.lumc.sasc.sentinel.utils.{ RunValidationException, getResourceFile }

trait ValidationAdapter { this: SentinelProcessor =>

  def schemaResourceUrl: String

  lazy val validator: RunValidator = new RunValidator(getResourceFile(schemaResourceUrl))

  private def isJson(fi: FileItem): Boolean = fi.tryJson.isSuccess

  def validate(json: JValue): Seq[ProcessingMessage] = validator.validationMessages(json)

  def validate(fi: FileItem): Seq[ProcessingMessage] =
    if (!isJson(fi)) {
      val pm = new ProcessingMessage
      pm.setMessage("Input file is not JSON-formatted.")
      Seq(pm)
    } else validator.validationMessages(fi.tryJson.get)

  def validateAndExtract(fi: FileItem): Try[JValue] = {
    val msgs = validate(fi)
    if (msgs.nonEmpty) Try(throw new RunValidationException("Gentrap run summary is invalid.", msgs))
    else Success(fi.tryJson.get)
  }
}
