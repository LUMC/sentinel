/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
