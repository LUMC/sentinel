package nl.lumc.sasc.sentinel.db

import java.io.{ ByteArrayInputStream, InputStream }
import scala.util.Try

import org.json4s.jackson.JsonMethods._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.{ InputAdapter, OutputAdapter }
import nl.lumc.sasc.sentinel.utils.getByteArray

trait DatabaseProvider { this: InputAdapter with OutputAdapter =>

  type DbId = String

  case class StoreRunResult(runId: DbId, refId: DbId, annotIds: Seq[DbId], sampleIds: Seq[DbId])

  def referenceCollectionName: String = "references"

  def annotationCollectionName: String = "annotations"

  def sampleCollectionName: String

  def storeRawInput(is: InputStream): Try[DbId]

  def storeReference(ref: Reference): Try[DbId]

  def storeSamples(samples: Seq[BaseSampleDocument]): Try[Seq[DbId]]

  def storeAnnotations(annots: Seq[Annotation]): Try[Seq[DbId]]

  def storeRun(fi: FileItem): Try[StoreRunResult] = {
      // NOTE: This stores the entire file in memory
      val fileContents = getByteArray(fi.getInputStream)
      val json = parse(new ByteArrayInputStream(fileContents))
      val validationMsgs = validate(json)

      if (validationMsgs.nonEmpty)
        // TODO: Implement nicer way to store all messages in a string
        Try(throw new IllegalArgumentException(validationMsgs.head.getMessage))
      else {
        // NOTE: This returns as an all-or-nothing operation, but it may fail midway (the price we pay for using Mongo).
        //       It does not break our application though, so it's an acceptable trade off.
        // TODO: Explore other types that are more expressive than Try to store state.
        for {
          runId <- storeRawInput(new ByteArrayInputStream(fileContents))
          ref <- Try(extractReference(json))
          refId <- storeReference(ref)
          annots <- Try(extractAnnotations(json))
          annotIds <- storeAnnotations(annots)
          samples <- Try(extractSamples(json, runId, refId, annotIds))
          sampleIds <- storeSamples(samples)
        } yield StoreRunResult(runId, refId, annotIds, sampleIds)
      }
    }
}

