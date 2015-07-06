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

import nl.lumc.sasc.sentinel.models.{ BaseLibRecord, BaseSampleRecord }
import nl.lumc.sasc.sentinel.processors.RunsProcessor

/**
 * Trait for storing samples and libraries from run summaries.
 *
 * @tparam S Subclass of [[nl.lumc.sasc.sentinel.models.BaseSampleRecord]] representing a sample run by a pipeline.
 * @tparam L Subclass of [[nl.lumc.sasc.sentinel.models.BaseLibRecord]] representing a library run by a pipeline.
 */
trait UnitsAdapter[S <: BaseSampleRecord, L <: BaseLibRecord] extends MongodbConnector { this: RunsProcessor =>

  /** Collection for the samples. */
  private lazy val samplesColl = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** Collection for the libraries. */
  private lazy val libsColl = mongo.db(collectionNames.pipelineLibs(pipelineName))

  /**
   * Stores the given sequence of samples into the sample collection.
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
   * Stores the given sequence of libraries into the sample collection.
   *
   * @param libs Libraries to store.
   * @return Bulk write operation result.
   */
  def storeLibs(libs: Seq[L])(implicit m: Manifest[L]): BulkWriteResult = {
    // TODO: refactor to use Futures instead
    val builder = libsColl.initializeUnorderedBulkOperation
    val docs = libs.map { case lib => grater[L].asDBObject(lib) }
    docs.foreach { case doc => builder.insert(doc) }
    builder.execute()
  }
}
