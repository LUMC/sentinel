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
package nl.lumc.sasc.sentinel.processors

import scala.concurrent.{ ExecutionContext, Future }

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFSDBFile
import com.novus.salat._
import com.novus.salat.global.ctx
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.adapters.{ FutureMongodbAdapter, ReadGroupsAdapter, SamplesAdapter }
import nl.lumc.sasc.sentinel.models._, Payloads._
import nl.lumc.sasc.sentinel.utils.SinglePathPatchJsonExtractor
import nl.lumc.sasc.sentinel.utils.Implicits.RunRecordDBObject

/** Class for performing operations on multiple pipeline runs. */
class CompositeRunsProcessor(protected val processors: Seq[RunsProcessor])
    extends FutureMongodbAdapter
    with SinglePathPatchJsonExtractor {
  require(processors.nonEmpty, "CompositeRunsProcessor must contain at least one RunsProcessor.")

  /** Database connection. */
  protected val mongo = {
    require(processors.map(_.mongo).distinct.size == 1,
      "CompositeRunsProcessor must combine only runs with the same database access.")
    processors.head.mongo
  }

  /** Runs collection that this processor works with. */
  private val coll = mongo.db(collectionNames.Runs)

  /** Overridable execution context for this processor. */
  protected def runsProcessorContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = runsProcessorContext

  /** Map of processor names and the actual object. */
  val processorsMap: Map[String, RunsProcessor] =
    processors.map(p => p.pipelineName -> p).toMap ensuring { map => map.size == processors.size }

  /**
   * Processes and stores the given uploaded file to the run records collection.
   *
   * @param pipelineName Name of the uploaded pipeline.
   * @param contents Upload contents as a byte array.
   * @param uploadName File name of the upload.
   * @param uploader Uploader of the run summary file.
   * @return A run record of the uploaded run summary file or a reason why the query fails.
   */
  def processRunUpload(pipelineName: String, contents: Array[Byte], uploadName: String,
                       uploader: User): Future[Perhaps[BaseRunRecord]] =
    processorsMap.get(pipelineName) match {
      case None       => Future.successful(InvalidPipelineError(processorsMap.keySet.toSeq).left)
      case Some(proc) => proc.processRunUpload(contents, uploadName, uploader)
    }

  /**
   * Retrieves all run records uploaded by the given user.
   *
   * @param user User performing the query. If he/she is not an admin, he/she will only be able to see the runs he/she
   *             uploaded.
   * @param pipelineNames If given, only return the runs of that pipeline. If not given, return runs from all pipelines.
   * @return Run records or the reason why the query fails.
   */
  def getRuns(user: User, pipelineNames: Seq[String] = Seq[String]()): Future[Perhaps[Seq[BaseRunRecord]]] = {

    val pipelinesToQuery =
      if (pipelineNames.isEmpty) processorsMap.keys.toSeq
      else pipelineNames

    val supportedPipelines = pipelinesToQuery
      .map(pn => processorsMap.get(pn))
      .collect { case Some(proc) => proc }

    if (supportedPipelines.length == pipelinesToQuery.length)
      Future
        .sequence(supportedPipelines.map(_.getRuns(user)))
        .map(_.flatten.right)
    else Future.successful(InvalidPipelineError(processorsMap.keys.toList).left)
  }

  /**
   * Retrieves an uploaded run file owned by the given user.
   *
   * If a run exists but the user ID is different, none is returned. Only when the query is performed by an admin
   * will the run be returned even if it was uploaded by someone else.
   *
   * @param runId ID of the run to retrieve.
   * @param user User performing the query.
   * @return Uploaded run file, if it exists.
   */
  def getRunFile(runId: ObjectId, user: User): Future[Option[GridFSDBFile]] = {
    val userCheck =
      if (user.isAdmin) MongoDBObject.empty
      else MongoDBObject("metadata.uploaderId" -> user.id)
    Future { mongo.gridfs.findOne(MongoDBObject("_id" -> runId) ++ userCheck) }
  }

  /**
   * Retrieves a single run record owned by the given user as a raw database object.
   *
   * If `ignoreDeletionStatus` is set to `false`, a run will only be returned if the user performing the query is an
   * admin and/or the user was the one who uploaded the run, and the run does not have the deletion flag
   * (`deletionTimeUtc`) set to a value. If `ignoreDeletionStatus` is set to `true`, the deletion flag status will
   * not affect whether the run is returned or not.
   *
   * @param runId ID of the run to retrieve.
   * @param user User performing the query.
   * @param ignoreDeletionStatus Whether to return runs that have been deleted or not.
   * @return Run record database object or a reason why the run can not be returned.
   */
  def getRunDbo(runId: ObjectId, user: User, ignoreDeletionStatus: Boolean = false,
                retrieveUnitsLabels: Boolean = false): Future[Perhaps[DBObject]] = {

    val query = MongoDBObject("_id" -> runId) ++ (if (user.isAdmin) MongoDBObject.empty
    else MongoDBObject("uploaderId" -> user.id))

    val maybeDbo = Future { coll.findOne(query) }
      .map {
        case None => RunIdNotFoundError.left
        case Some(dbo) => dbo.getAs[java.util.Date]("deletionTimeUtc") match {
          case Some(_) if !ignoreDeletionStatus => ResourceGoneError.left
          case otherwise                        => dbo.right
        }
      }

    val action: AsyncPerhaps[DBObject] = for {
      dbo <- ? <~ maybeDbo
      pipelineName <- ? <~ dbo.pipelineName
      processor <- ? <~ processorsMap.get(pipelineName)
        .toRightDisjunction(UnexpectedDatabaseError(s"Run ID $runId was created by an unsupported pipeline."))

      // Launch info retrieval jobs asynchronously
      samplesLabelsRetrieval = processor match {
        case sa: SamplesAdapter if retrieveUnitsLabels => sa.getSamplesLabels(dbo.sampleIds.toSet)
        case otherwise                                 => Future.successful(Map.empty[String, SampleLabelsLike].right)
      }
      readGroupsLabelsRetrieval = processor match {
        case rga: ReadGroupsAdapter if retrieveUnitsLabels => rga.getReadGroupsLabels(dbo.readGroupIds.toSet)
        case otherwise                                     => Future.successful(Map.empty[String, ReadGroupLabelsLike].right)
      }
      sLabels <- ? <~ samplesLabelsRetrieval
      rgLabels <- ? <~ readGroupsLabelsRetrieval
      rdbo = if (retrieveUnitsLabels) dbo ++ MongoDBObject("sampleLabels" -> sLabels, "readGroupLabels" -> rgLabels)
      else dbo ++ MongoDBObject.empty
    } yield rdbo

    action.run
  }

  /**
   * Retrieves a single run record.
   *
   * @param runId ID of the run to retrieve.
   * @param user User performing the query.
   * @return Run record or a reason why the run can not be returned.
   */
  def getRun(runId: ObjectId, user: User, retrieveUnitsLabels: Boolean = false): Future[Perhaps[BaseRunRecord]] = {
    val res: AsyncPerhaps[BaseRunRecord] = for {
      dbo <- ? <~ getRunDbo(runId, user, retrieveUnitsLabels = retrieveUnitsLabels)
        .map {
          // We change the HTTP 410 error to 404 since 410 should only be returned when a DELETE request tries to delete
          // an already deleted run.
          case -\/(err) if err.actionStatusCode == 410 => RunIdNotFoundError.left
          case otherwise                               => otherwise
        }
      pipelineName <- ? <~ dbo.pipelineName
      processor <- ? <~ processorsMap.get(pipelineName)
        .toRightDisjunction(UnexpectedDatabaseError(s"Run ID $runId was created by an unsupported pipeline."))
      rec <- ? <~ processor.dbo2Run(dbo)
        .toRightDisjunction(UnexpectedDatabaseError("Error when trying to convert raw database entry into an object."))
    } yield rec

    res.run
  }

  /**
   * Applies the given patch operations to the run with the given ID.
   *
   * @param runId ID of the run to patch.
   * @param rawPatch Byte array of the raw request patch.
   * @return Either error messages or the number of (run, samples, read groups) updated.
   */
  def patchAndUpdateRun(runId: ObjectId, user: User, rawPatch: Array[Byte]): Future[Perhaps[(Int, Int, Int)]] = {
    val patching = for {
      patches <- ? <~ extractAndValidatePatches(rawPatch)
      dbo <- ? <~ getRunDbo(runId, user)
        .map {
          case -\/(err) if err.actionStatusCode == 410 => RunIdNotFoundError.left
          case otherwise                               => otherwise
        }
      pipelineName <- ? <~ dbo.pipelineName
      processor <- ? <~ processorsMap.get(pipelineName)
        .toRightDisjunction(UnexpectedDatabaseError(s"Run ID $runId was created by an unsupported pipeline."))
      res <- ? <~ processor.patchAndUpdateRunDbo(dbo, user, patches.toList)
    } yield res

    patching.run
  }

  /**
   * Deletes the run with the given ID.
   *
   * The deletion call cascades to all subunits (samples and/or read groups) that are linked to the run.
   *
   * @param runId ID of the run to delete.
   * @param user User performing the query.
   * @return The deleted run record with a `deletionTimeUtc` attribute showing the time of deletion, or the reason why
   *         the deletion does not succeed.
   */
  def deleteRun(runId: ObjectId, user: User): Future[Perhaps[BaseRunRecord]] = {
    val deletion: AsyncPerhaps[BaseRunRecord] = for {
      dbo <- ? <~ getRunDbo(runId, user)
      pipelineName <- ? <~ dbo.pipelineName
      processor <- ? <~ processorsMap.get(pipelineName)
        .toRightDisjunction(UnexpectedDatabaseError(s"Run ID $runId was created by an unsupported pipeline."))
      res <- ? <~ processor.deleteRunDbo(dbo, user)
    } yield res

    deletion.run
  }

  /**
   * Retrieves general statistics of all uploaded runs.
   *
   * @return Objects containing statistics of each supported pipeline type.
   */
  def getGlobalRunStats(): Future[Seq[PipelineStats]] = Future {
    val statsGrater = grater[PipelineStats]
    coll
      .aggregate(List(
        MongoDBObject("$match" ->
          MongoDBObject("deletionTimeUtc" -> MongoDBObject("$exists" -> false))
        ),
        MongoDBObject("$project" ->
          MongoDBObject("_id" -> 0, "pipeline" -> 1, "nSamples" -> 1, "nReadGroups" -> 1)),
        MongoDBObject("$group" ->
          MongoDBObject(
            "_id" -> "$pipeline",
            "nRuns" -> MongoDBObject("$sum" -> 1),
            "nSamples" -> MongoDBObject("$sum" -> "$nSamples"),
            "nReadGroups" -> MongoDBObject("$sum" -> "$nReadGroups"))),
        MongoDBObject("$sort" -> MongoDBObject("_id" -> 1))),
        AggregationOptions(AggregationOptions.CURSOR))
      .map { pstat => statsGrater.asObject(pstat) }
      .toSeq
  }
}
