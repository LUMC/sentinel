package nl.lumc.sasc.sentinel.db

import java.io.InputStream
import java.time.Clock
import java.util.Date

import com.github.fge.jsonschema.core.report.ProcessingMessage
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.RunRecord
import nl.lumc.sasc.sentinel.utils.getResourceFile
import nl.lumc.sasc.sentinel.validation.RunValidator
import org.json4s.JValue
import org.scalatra.servlet.FileItem

import scala.util.Try

trait RunAdapter extends IndexedCollectionAdapter {

  this: MongodbConnector with SamplesAdapter with AnnotationsAdapter with ReferencesAdapter =>

  def runsCollectionName: String

  def processRun(fi: FileItem, userId: String, pipeline: String): Try[RunRecord]

  def validator: RunValidator

  protected def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  protected def getSchemaValidator(schemaUrl: String) = new RunValidator(getSchema(schemaUrl))

  def validate(runJson: JValue): Seq[ProcessingMessage] = validator.validationMessages(runJson)

  private lazy val coll = mongo.db(runsCollectionName)

  def storeFile(ins: InputStream, fileName: String, unzipped: Boolean): DbId = {
    mongo.gridfs(ins) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject("inputGzipped" -> unzipped)
    }.get.toString
  }

  def createRun(fileId: DbId, refId: DbId, annotIds: Seq[DbId], samples: Seq[SampleDocument],
               userId: String, pipeline: String) = {
    val run = RunRecord(
      runId = fileId, // NOTE: runId kept intentionally the same as fileId
      refId = refId,
      annotIds = annotIds,
      creationTime = Date.from(Clock.systemUTC().instant),
      uploader = userId,
      pipeline = pipeline,
      nSamples = samples.size,
      nLibsPerSample = samples.map(_.libs.size))
    coll.insert(grater[RunRecord].asDBObject(run))
    run
  }
}
