package nl.lumc.sasc.sentinel.db

import com.mongodb.casbah.BulkWriteResult
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.BaseSampleDocument

trait SamplesAdapter { this: MongodbConnector =>

  type SampleDocument <: BaseSampleDocument

  def samplesCollectionName: String

  private lazy val coll = mongo.db(samplesCollectionName)

  def storeSamples(samples: Seq[SampleDocument])(implicit m: Manifest[SampleDocument]): BulkWriteResult = {
    val builder = coll.initializeUnorderedBulkOperation
    val docs = samples.map { case sample => grater[SampleDocument].asDBObject(sample) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }
}
