package nl.lumc.sasc.sentinel.processors.unsupported

import java.time.Clock
import java.util.Date
import nl.lumc.sasc.sentinel.models.RunDocument
import scala.util.Try

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.utils.implicits._
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

class UnsupportedInputProcessor(protected val mongo: MongodbAccessObject)
    extends MongodbConnector
    with RunsAdapter
    with ValidationAdapter {

  val validator = createValidator("/schemas/unsupported.json")

  def processRun(fi: FileItem, userId: String, pipeline: String) =
    for {
      (byteContents, unzipped) <- Try(fi.readInputStream())
      _ <- Try(parseAndValidate(byteContents))
      fileId <- Try(storeFile(byteContents, userId, pipeline, fi.getName, unzipped))
      run = RunDocument(fileId, userId, pipeline, 0, 0, Date.from(Clock.systemUTC().instant))
      _ <- Try(storeRun(run))
    } yield run
}
