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
import scala.util.Try
import scala.util.matching.Regex

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.BulkWriteResult
import com.novus.salat.{ CaseClass => _, _ }
import org.bson.types.ObjectId
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.JsonPatch._
import nl.lumc.sasc.sentinel.models.Payloads.{ PatchValidationError, UnexpectedDatabaseError }
import nl.lumc.sasc.sentinel.utils.Implicits._

/**
 * Trait for storing read groups from run summaries.
 *
 * The trait extends SamplesAdapter because for all read group-level information must have a sample information.
 */
trait ReadGroupsAdapter extends SamplesAdapter {

  /** Read group-level metrics container. */
  type ReadGroupRecord <: BaseReadGroupRecord with CaseClass

  /** Manifest for ReadGroupRecord. */
  def readGroupManifest: Manifest[ReadGroupRecord]

  /** Pipeline name of read group. */
  def pipelineName: String

  /** Overridable execution context for this adapter. */
  protected def readGroupsAdapterExecutionContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = readGroupsAdapterExecutionContext

  /** Collection of the units. */
  private lazy val coll = mongo.db(collectionNames.pipelineReadGroups(pipelineName))

  /** Function for updating a single read group database raw object. */
  private val updateReadGroupDbo = updateDbo(coll) _

  /** Default patch functions for the read groups of a given run. */
  val readGroupsPatchFunc: DboPatchFunction = List(
    ReadGroupsAdapter.labelsPF,
    UnitsAdapter.tagsPF,
    UnitsAdapter.defaultPF).reduceLeft { _ orElse _ }

  /**
   * Stores the given sequence of read group metrics into its collection.
   *
   * @param readGroups Read groups to store.
   * @return Bulk write operation result.
   */
  protected[adapters] def storeReadGroups(readGroups: Seq[ReadGroupRecord]): Future[BulkWriteResult] =
    Future {
      val builder = coll.initializeUnorderedBulkOperation
      val recordGrater = grater[ReadGroupRecord](SalatContext, readGroupManifest)
      val docs = readGroups.map { readGroup => recordGrater.asDBObject(readGroup) }
      docs.foreach { doc => builder.insert(doc) }
      builder.execute()
    }

  /** Retrieves the raw database records of the given read group record IDs. */
  def getReadGroupDbos(ids: Seq[ObjectId], extraQuery: DBObject = MongoDBObject.empty) =
    getUnitDbos(coll)(ids, extraQuery)

  /** Retrieves the read group records of the given read group database IDs. */
  def getReadGroups(ids: Seq[ObjectId], extraQuery: DBObject = MongoDBObject.empty) = {
    val retrieval = for {
      readGroupDbos <- ? <~ getReadGroupDbos(ids, extraQuery)
    } yield readGroupDbos.map(dbo => grater[ReadGroupRecord](SalatContext, readGroupManifest).asObject(dbo))

    retrieval.run
  }

  def getReadGroupsLabels(ids: Seq[ObjectId]): Future[Perhaps[Map[String, ReadGroupLabelsLike]]] = {
    val retrieval = for {
      readGroups <- ? <~ getReadGroups(ids)
      infos <- ? <~ readGroups.map { rg => rg.dbId.toString -> rg.labels }.toMap
    } yield infos

    retrieval.run
  }

  /** Retrieves the read group IDs of the given read sample IDs. */
  def getReadGroupIds(sampleIds: Seq[ObjectId]): Future[Perhaps[Seq[ObjectId]]] = {
    val retrieval = for {
      dbos <- ? <~ Future {
        val query = MongoDBObject("sampleId" -> MongoDBObject("$in" -> sampleIds))
        coll.find(query).toSeq
      }
      rgids <- ? <~ {
        val maybeIds = dbos.map(_._id)
        val okIds = maybeIds.collect { case Some(okId) => okId }
        if (okIds.length == sampleIds.length) okIds.right
        else {
          val badIds = maybeIds.zip(sampleIds)
            .collect { case (None, badId) => badId }
          val badIdsStr = badIds.mkString("', '")
          Payloads.UnexpectedDatabaseError(s"Sample IDs not found: '$badIdsStr'.").left
        }
      }
    } yield rgids

    retrieval.run
  }

  /**
   * Updates existing sample database objects.
   *
   * @param dbos Raw database objects.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or a sequence of write results.
   */
  def updateReadGroupDbos(dbos: Seq[DBObject]): Future[Perhaps[Seq[WriteResult]]] =
    dbos.map(updateReadGroupDbo).toList
      .traverse[AsyncPerhaps, WriteResult] { EitherT(_) }
      .run

  /**
   * Patches the read group with the given database ID.
   *
   * @param readGroupId Database IDs of the read group records.
   * @param patches Patches to perform.
   * @param patchFunc Partial functions to apply the patch.
   * @return A future containing an error [[nl.lumc.sasc.sentinel.models.ApiPayload]] or the number of updated records.
   */
  def patchReadGroupDbo(readGroupId: ObjectId,
                        patches: List[PatchOp])(patchFunc: DboPatchFunction): Future[Perhaps[Seq[DBObject]]] = {
    val res = for {
      readGroupDbos <- ? <~ getReadGroupDbos(Seq(readGroupId))
      patchedReadGroupDbos <- ? <~ patchDbos(readGroupDbos, patches)(patchFunc)
    } yield patchedReadGroupDbos

    res.run
  }

  /**
   * Deletes the read groups with the given IDs.
   *
   * @param readGroupIds Seq of read group database IDs.
   * @return
   */
  def deleteReadGroups(readGroupIds: Seq[ObjectId]): Future[BulkWriteResult] =
    Future {
      val deleter = coll.initializeUnorderedBulkOperation
      val query = MongoDBObject("_id" -> MongoDBObject("$in" -> readGroupIds))
      deleter.find(query).remove()
      deleter.execute()
    }
}

object ReadGroupsAdapter {

  private val replaceablePaths = Seq("/labels/runName", "/labels/sampleName", "/labels/readGroupName")

  /** 'replace' patch for 'labels' in a single read group. */
  val labelsPF: DboPatchFunction = {

    case (dbo: DBObject, patch @ AddOp(_, _: String)) if replaceablePaths.contains(patch.path) =>
      UnitsAdapter.labelsAddOrReplacePF(dbo, patch)

    case (dbo: DBObject, patch @ ReplaceOp(_, _: String)) if replaceablePaths.contains(patch.path) =>
      UnitsAdapter.labelsAddOrReplacePF(dbo, patch)
  }
}
