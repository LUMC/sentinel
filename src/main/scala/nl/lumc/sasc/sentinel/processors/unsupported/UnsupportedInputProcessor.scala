package nl.lumc.sasc.sentinel.processors.unsupported

import java.time.Clock
import java.util.Date
import nl.lumc.sasc.sentinel.models.RunDocument
import scala.util.Try

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.processors.SentinelProcessor
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

class UnsupportedInputProcessor(protected val mongo: MongodbAccessObject)
  extends SentinelProcessor
  with RunsAdapter
  with ValidationAdapter
  with MongodbConnector {

  val schemaResourceUrl = "/schemas/unsupported.json"

  def processRun(fi: FileItem, userId: String, pipeline: String) =
    validateAndExtract(fi).flatMap { case json =>
      for {
        fileId <- Try(storeFile(fi.byteStream, userId, pipeline, fi.getName, fi.inputUnzipped))
        run = RunDocument(fileId, userId, pipeline, 0, 0, Date.from(Clock.systemUTC().instant))
        _ <- Try(storeRun(run))
      } yield run
    }
}
