package nl.lumc.sasc.sentinel.adapters

import scala.util.Try

import org.json4s.JValue

import nl.lumc.sasc.sentinel.db.MongodbConnector
import nl.lumc.sasc.sentinel.models.BaseSampleDocument

trait SamplesAdapter { this: MongodbConnector =>

  type SampleDocument <: BaseSampleDocument

  def sampleCollectionName: String

  def extractSamples(runJson: JValue, runId: DbId, refId: DbId, annotIds: Seq[DbId]): Seq[SampleDocument]

  def storeSamples(samples: Seq[BaseSampleDocument]): Try[Seq[DbId]] = scala.util.Success(Seq(""))
}
