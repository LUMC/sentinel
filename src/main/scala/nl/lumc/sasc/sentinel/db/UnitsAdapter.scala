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

import nl.lumc.sasc.sentinel.models.{ BaseReadGroupRecord, BaseSampleRecord }
import nl.lumc.sasc.sentinel.processors.RunsProcessor

/**
 * Trait for storing samples and read groups from run summaries.
 *
 * @tparam S Subclass of [[nl.lumc.sasc.sentinel.models.BaseSampleRecord]] representing a sample sample-level metrics.
 * @tparam R Subclass of [[nl.lumc.sasc.sentinel.models.BaseReadGroupRecord]] representing a read group-level metrics.
 */
trait UnitsAdapter[S <: BaseSampleRecord, R <: BaseReadGroupRecord] extends MongodbConnector { this: RunsProcessor =>

  /** Collection for the samples. */
  private lazy val samplesColl = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** Collection for the read groups. */
  private lazy val readGroupsColl = mongo.db(collectionNames.pipelineReadGroups(pipelineName))

  /**
   * Stores the given sequence of sample metrics into its collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  def storeSamples(samples: Seq[S])(implicit m: Manifest[S]): BulkWriteResult = {
    // TODO: refactor to use Futures instead
    val builder = samplesColl.initializeUnorderedBulkOperation
    val docs = samples.map { case sample => grater[S].asDBObject(sample) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }

  /**
   * Stores the given sequence of read group metrics into its collection.
   *
   * @param readGroups Read groups to store.
   * @return Bulk write operation result.
   */
  def storeReadGroups(readGroups: Seq[R])(implicit m: Manifest[R]): BulkWriteResult = {
    // TODO: refactor to use Futures instead
    val builder = readGroupsColl.initializeUnorderedBulkOperation
    val docs = readGroups.map { case rg => grater[R].asDBObject(rg) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }
}
