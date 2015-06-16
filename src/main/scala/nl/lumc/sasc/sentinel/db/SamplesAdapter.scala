package nl.lumc.sasc.sentinel.db

import com.mongodb.casbah.BulkWriteResult
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.BaseSampleDocument

/**
 * Trait for storing samples from run summaries.
 *
 * @tparam T Subclass of [[nl.lumc.sasc.sentinel.models.BaseSampleDocument]] representing a sample run by a pipeline.
 */
trait SamplesAdapter[T <: BaseSampleDocument] extends MongodbConnector { this: RunsAdapter =>

  /** Name of the pipeline that produces the run summary file. */
  def pipelineName: String

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /**
   * Stores the given sequence of samples into the sample collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  def storeSamples(samples: Seq[T])(implicit m: Manifest[T]): BulkWriteResult = {
    // TODO: refactor to use Futures instead
    val builder = coll.initializeUnorderedBulkOperation
    val docs = samples.map { case sample => grater[T].asDBObject(sample) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }
}
