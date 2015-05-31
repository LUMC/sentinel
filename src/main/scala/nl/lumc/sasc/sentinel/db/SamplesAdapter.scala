package nl.lumc.sasc.sentinel.db

import com.mongodb.casbah.BulkWriteResult
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.{ BaseRunDocument, BaseSampleDocument }

trait SamplesAdapter[T <: BaseSampleDocument] extends MongodbConnector { this: RunsAdapter =>

  def pipelineName: String

  private lazy val coll = mongo.db(collectionNames.pipelineSamples(pipelineName))

  def storeSamples(samples: Seq[T])(implicit m: Manifest[T]): BulkWriteResult = {
    val builder = coll.initializeUnorderedBulkOperation
    val docs = samples.map { case sample => grater[T].asDBObject(sample) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }

  def deleteSamples(run: BaseRunDocument): Unit = ???
}
