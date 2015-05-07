package nl.lumc.sasc.sentinel.db

import java.io.{ ByteArrayInputStream, InputStream }
import scala.io.Source
import scala.util.Try

import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunProcessor

trait DatabaseProvider { this: RunProcessor =>

  type DbId = String

  def storeRun(is: InputStream): Try[DbId]

  def storeReference(ref: Reference): Try[DbId]

  def storeSamples(samples: Seq[BaseSampleDocument]): Try[Seq[DbId]]

  def storeAnnotations(annots: Seq[Annotation]): Try[Seq[DbId]]

  def process(is: InputStream): Try[Seq[DbId]] = {
      // NOTE: this stores the entire file in memory
      val fileContents = Source.fromInputStream(is).map(_.toByte).toArray
      val json = parse(new ByteArrayInputStream(fileContents))
      val validationMsgs = validate(json)

      if (validationMsgs.nonEmpty)
        // TODO: nicer way to store all messages in a string?
        Try(throw new IllegalArgumentException(validationMsgs.head.getMessage))
      else {
        for {
          runId <- storeRun(new ByteArrayInputStream(fileContents))
          annots <- Try(extractAnnotations(json))
          annotIds <- storeAnnotations(annots)
          ref <- Try(extractReference(json))
          refId <- storeReference(ref)
          samples <- Try(extractSamples(json, runId, refId, annotIds))
          sampleIds <- storeSamples(samples)
        } yield sampleIds
      }
    }
}

