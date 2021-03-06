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
import com.novus.salat.{CaseClass => _, _}
import org.bson.types.ObjectId
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.JsonPatch._

/**
 * Trait for storing samples from run summaries.
 */
trait SamplesAdapter extends UnitsAdapter {

  /** Sample-level metrics container. */
  type SampleRecord <: BaseSampleRecord with CaseClass

  /** Manifest for SampleRecord. */
  def sampleManifest: Manifest[SampleRecord]

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

  /** Default patch functions for the samples of a given run. */
  val samplesPatchFunc: DboPatchFunction = List(
    SamplesAdapter.labelsPF,
    UnitsAdapter.tagsPF,
    UnitsAdapter.notesPF,
    UnitsAdapter.defaultPF
  ).reduceLeft { _ orElse _ }

  /**
   * Stores the given sequence of sample metrics into its collection.
   *
   * @param samples Samples to store.
   * @return Bulk write operation result.
   */
  protected[adapters] def storeSamples(samples: Seq[SampleRecord]): Future[BulkWriteResult] =
    Future {
      val builder = coll.initializeUnorderedBulkOperation
      val recordGrater = grater[SampleRecord](SalatContext, sampleManifest)
      val docs = samples.map { sample => recordGrater.asDBObject(sample) }
      docs.foreach { doc => builder.insert(doc) }
      builder.execute()
    }

  /** Retrieves the raw database records of the given sample record IDs. */
  def getSampleDbos(ids: Seq[ObjectId], extraQuery: DBObject = MongoDBObject.empty) =
    getUnitDbos(coll)(ids, extraQuery)

  /** Retrieves the sample records of the given sample database IDs. */
  def getSamples(ids: Seq[ObjectId], extraQuery: DBObject = MongoDBObject.empty): Future[Perhaps[Seq[SampleRecord]]] = {
    val retrieval = for {
      sampleDbos <- ? <~ getSampleDbos(ids, extraQuery)
      samples = sampleDbos.map(dbo => grater[SampleRecord](SalatContext, sampleManifest).asObject(dbo))
    } yield samples

    retrieval.run
  }

  def getSamplesLabels(ids: Seq[ObjectId]): Future[Perhaps[Map[String, SampleLabelsLike]]] = {
    val retrieval = for {
      samples <- ? <~ getSamples(ids)
      infos <- ? <~ samples.map { sample => sample.dbId.toString -> sample.labels }.toMap
    } yield infos

    retrieval.run
  }

  /**
   * Updates existing sample database objects.
   *
   * @param dbos Raw database objects.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or a sequence of write results.
   */
  def updateSampleDbos(dbos: Seq[DBObject]): Future[Perhaps[Seq[WriteResult]]] =
    dbos.map(updateSampleDbo).toList
      .traverse[AsyncPerhaps, WriteResult] { EitherT(_) }
      .run

  /**
   * Patches the sample with the given database IDs.
   *
   * @param sampleId Database ID of the sample records.
   * @param patches Patches to perform.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or the number of updated records.
   */
  def patchSampleDbo(
    sampleId: ObjectId,
    patches:  List[PatchOp]
  ): Future[Perhaps[Seq[DBObject]]] = {
    val res = for {
      sampleDbos <- ? <~ getSampleDbos(Seq(sampleId))
      patchedSampleDbos <- ? <~ patchDbos(sampleDbos, patches)(samplesPatchFunc)
    } yield patchedSampleDbos

    res.run
  }

  /**
   * Deletes the samples with the given IDs.
   *
   * @param sampleIds Seq of sample database IDs.
   * @return
   */
  def deleteSamples(sampleIds: Seq[ObjectId]): Future[BulkWriteResult] =
    Future {
      val deleter = coll.initializeUnorderedBulkOperation
      val query = MongoDBObject("_id" -> MongoDBObject("$in" -> sampleIds))
      deleter.find(query).remove()
      deleter.execute()
    }
}

object SamplesAdapter {

  private val replaceablePaths = Seq("/labels/runName", "/labels/sampleName")

  /** 'replace' patch for 'sampleName' in a single sample */
  val labelsPF: DboPatchFunction = {

    case (dbo: DBObject, patch @ AddOp(_, _: String)) if replaceablePaths.contains(patch.path) =>
      UnitsAdapter.labelsAddOrReplacePF(dbo, patch)

    case (dbo: DBObject, patch @ ReplaceOp(_, _: String)) if replaceablePaths.contains(patch.path) =>
      UnitsAdapter.labelsAddOrReplacePF(dbo, patch)
  }
}
