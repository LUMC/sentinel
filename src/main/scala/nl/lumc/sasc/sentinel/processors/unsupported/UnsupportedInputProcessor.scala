package nl.lumc.sasc.sentinel.processors.unsupported

import java.io.ByteArrayInputStream
import java.time.Clock
import java.util.Date
import nl.lumc.sasc.sentinel.models.RunDocument
import scala.util.Try

import org.json4s.jackson.JsonMethods._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.validation.ValidationAdapter
import nl.lumc.sasc.sentinel.utils._

class UnsupportedInputProcessor(protected val mongo: MongodbAccessObject)
  extends MongodbConnector
  with RunsAdapter
  with ValidationAdapter {

  val validator = getSchemaValidator("unsupported.json")

  def processRun(fi: FileItem, userId: String, pipeline: String) =  {

    // NOTE: This stores the entire file in memory
    val (fileContents, unzipped) = getByteArray(fi.getInputStream)
    val json = parse(new ByteArrayInputStream(fileContents))
    val validationMsgs = validate(json)

    if (validationMsgs.nonEmpty)
      Try(throw new RunValidationException("Uploaded run summary is invalid.", validationMsgs))
    else {
      for {
        fileId <- Try(storeFile(new ByteArrayInputStream(fileContents), userId, pipeline, fi.getName, unzipped))
        run = RunDocument(fileId, userId, pipeline, 0, 0, Date.from(Clock.systemUTC().instant))
        _ <- Try(storeRun(run))
      } yield run
    }
  }
}
