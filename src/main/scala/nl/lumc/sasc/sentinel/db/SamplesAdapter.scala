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

import nl.lumc.sasc.sentinel.models.BaseSampleRecord
import nl.lumc.sasc.sentinel.processors.RunsProcessor

/**
 * Trait for storing samples from run summaries.
 *
 * @tparam T Subclass of [[nl.lumc.sasc.sentinel.models.BaseSampleRecord]] representing a sample sample-level metrics.
 */
trait SamplesAdapter[T <: BaseSampleRecord] extends MongodbConnector { this: RunsProcessor =>

  /** Collection of the units. */
  private lazy val coll = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /**
   * Stores the given sequence of sample metrics into its collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  protected def storeSamples(samples: Seq[T])(implicit m: Manifest[T]): BulkWriteResult = {
    // TODO: refactor to use Futures instead
    val builder = coll.initializeUnorderedBulkOperation
    val docs = samples.map { case sample => grater[T].asDBObject(sample) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }
}
