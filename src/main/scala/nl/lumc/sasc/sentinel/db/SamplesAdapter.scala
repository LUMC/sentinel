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

import scala.concurrent._

import com.mongodb.casbah.BulkWriteResult
import com.novus.salat.{ CaseClass => _, _ }
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.CaseClass
import nl.lumc.sasc.sentinel.models.BaseSampleRecord
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.FutureAdapter

/**
 * Trait for storing samples from run summaries.
 */
trait SamplesAdapter
    extends MongodbConnector
    with FutureAdapter { this: RunsProcessor =>

  /** Sample-level metrics container. */
  type SampleRecord <: BaseSampleRecord with CaseClass

  /** Overridable execution context for this adapter. */
  protected def samplesAdapterExecutionContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = samplesAdapterExecutionContext

  /** Collection of the units. */
  private lazy val coll = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /**
   * Stores the given sequence of sample metrics into its collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  protected def storeSamples(samples: Seq[SampleRecord])(implicit m: Manifest[SampleRecord]): Future[BulkWriteResult] =
    Future {
      val builder = coll.initializeUnorderedBulkOperation
      val docs = samples.map { case sample => grater[SampleRecord].asDBObject(sample) }
      docs.foreach { case doc => builder.insert(doc) }
      builder.execute()
    }
}
