/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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
package nl.lumc.sasc.sentinel.adapters

import scala.concurrent._

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.BulkWriteResult
import com.novus.salat.{ CaseClass => _, _ }
import com.novus.salat.global._
import org.bson.types.ObjectId
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.{ BaseSampleRecord, CaseClass, SinglePathPatch }

/**
 * Trait for storing samples from run summaries.
 */
trait SamplesAdapter extends UnitsAdapter {

  /** Sample-level metrics container. */
  type SampleRecord <: BaseSampleRecord with CaseClass

  /** Pipeline name of sample. */
  def pipelineName: String

  /** Overridable execution context for this adapter. */
  protected def samplesAdapterExecutionContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = samplesAdapterExecutionContext

  /** Collection of the units. */
  private lazy val coll = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** Function for updating a single sample database raw object. */
  private val updateSampleDbo = updateDbo(coll) _

  /**
   * Stores the given sequence of sample metrics into its collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  protected[adapters] def storeSamples(samples: Seq[SampleRecord])(implicit m: Manifest[SampleRecord]): Future[BulkWriteResult] =
    Future {
      val builder = coll.initializeUnorderedBulkOperation
      val recordGrater = grater[SampleRecord]
      val docs = samples.map { sample => recordGrater.asDBObject(sample) }
      docs.foreach { doc => builder.insert(doc) }
      builder.execute()
    }

  /** Retrieves the raw database records of the given sample record IDs. */
  def getSampleRecordsDbo(ids: Set[ObjectId], extraQuery: DBObject = MongoDBObject.empty) =
    getUnitRecordsDbo(coll)(ids, extraQuery)

  /**
   * Updates existing sample database objects.
   *
   * @param dbos Raw database objects.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or a sequence of write results.
   */
  def updateSamplesDbo(dbos: Seq[DBObject]): Future[Perhaps[Seq[WriteResult]]] =
    Future
      .sequence(dbos.map(updateSampleDbo))
      .map {
        case res =>
          val oks = res.collect { case \/-(ok) => ok }
          if (oks.length == dbos.length) oks.right
          else res
            .collect { case -\/(nope) => nope }
            .reduceLeft { _ |+| _ }
            .left
      }

  /**
   * Patches the samples with the given database IDs.
   *
   * @param sampleIds Database IDs of the sample records.
   * @param patches Patches to perform.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or the number of updated records.
   */
  def patchAndUpdateSampleRecords(sampleIds: Seq[ObjectId], patches: List[SinglePathPatch],
                                  patchFunc: PatchFunc): Future[Perhaps[Int]] = {
    val res = for {
      sampleDbos <- ? <~ getSampleRecordsDbo(sampleIds.toSet)
      patchedSampleDbos <- ? <~ patchDbos(sampleDbos, patches)(patchFunc)
      writeResults <- ? <~ updateSamplesDbo(patchedSampleDbos)
      nUpdated = writeResults.map(_.getN).sum
      if sampleIds.length == nUpdated
    } yield nUpdated

    res.run
  }
}
