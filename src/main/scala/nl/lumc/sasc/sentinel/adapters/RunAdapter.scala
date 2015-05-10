package nl.lumc.sasc.sentinel.adapters

import java.io.InputStream
import scala.util.Try

import com.github.fge.jsonschema.core.report.ProcessingMessage
import nl.lumc.sasc.sentinel.db.MongodbConnector
import nl.lumc.sasc.sentinel.utils.getResourceFile
import nl.lumc.sasc.sentinel.validation.RunValidator
import org.json4s.JValue

trait RunAdapter { this: MongodbConnector =>

  def validator: RunValidator

  protected def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  protected def getSchemaValidator(schemaUrl: String) = new RunValidator(getSchema(schemaUrl))

  def validate(runJson: JValue): Seq[ProcessingMessage] = validator.validationMessages(runJson)

  def storeRawRun(ins: InputStream, fileName: String, contentType: String = "application/json") = {
    val id = db.gridfs(ins) { f =>
      f.filename = fileName
      f.contentType = contentType
    }
    Try(id.get.toString)
  }
}
