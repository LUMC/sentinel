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
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.adapters.FutureMongodbAdapter
import nl.lumc.sasc.sentinel.models.{ BaseRunRecord, Payloads, User }, Payloads._
import nl.lumc.sasc.sentinel.utils.Implicits.RunRecordDBObject

/** Class for combining multiple [[nl.lumc.sasc.sentinel.processors.RunsProcessor]]s. */
class CompositeRunsProcessor(protected val processors: Seq[RunsProcessor]) extends FutureMongodbAdapter {
  require(processors.nonEmpty, "CompositeRunsProcessor must contain at least one RunsProcessor.")

  protected val mongo = {
    require(processors.map(_.mongo).distinct.size == 1,
      "CompositeRunsProcessor must combine only runs with the same database access.")
    processors.head.mongo
  }

  /** Helper class for patching runs. */
  val patcher = RunsPatcher

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
   * @return A run record of the uploaded run summary file.
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
   * @param user Run summary files uploader.
   * @return Run records.
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
   * If a run exists but the user ID is different, none is returned. A deleted run record (a run record without
   * the corresponding run summary file, marked with the `deletionTimeUtc` key) will also return none.
   *
   * @param runId ID of the run to retrieve.
   * @param user Run uploader.
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
   * If a run exists but the user ID is different, none is returned. A deleted run record (a run record without
   * the corresponding run summary file, marked with the `deletionTimeUtc` key) will also return none.
   *
   * @param runId ID of the run to retrieve.
   * @param user Run uploader.
   * @return Run record database object, if it exists.
   */
  def getRunRecordDbo(runId: ObjectId, user: User): Future[Option[DBObject]] = {
    val userCheck =
      if (user.isAdmin) MongoDBObject.empty
      else MongoDBObject("uploaderId" -> user.id)

    Future {
      coll
        .findOne(MongoDBObject("_id" -> runId, "deletionTimeUtc" -> MongoDBObject("$exists" -> false)) ++ userCheck)
    }
  }

  /**
   * Retrieves a single run record owned by the given user.
   *
   * If a run exists but the user ID is different, none is returned. A deleted run record (a run record without
   * the corresponding run summary file, marked with the `deletionTimeUtc` key) will also return none.
   *
   * @param runId ID of the run to retrieve.
   * @param user Run uploader.
   * @return Run record, if it exists.
   */
  def getRunRecord(runId: ObjectId, user: User): Future[Perhaps[BaseRunRecord]] = {
    val res = for {
      dbo <- ? <~ getRunRecordDbo(runId, user).map(_.toRightDisjunction(RunIdNotFoundError))
      pipelineName <- ? <~ dbo.pipelineName
      rec <- ? <~ processorsMap.get(pipelineName)
        .flatMap { proc => proc.dbo2RunRecord(dbo) }
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
  def patchAndUpdateRunRecord(runId: ObjectId, user: User, rawPatch: Array[Byte]): Future[Perhaps[(Int, Int, Int)]] = {
    val patching = for {
      patches <- ? <~ patcher.extractAndValidatePatches(rawPatch)
      dbo <- ? <~ getRunRecordDbo(runId, user).map(_.toRightDisjunction(RunIdNotFoundError))
      pipelineName <- ? <~ dbo.pipelineName
      processor <- ? <~ processorsMap.get(pipelineName)
        .toRightDisjunction(UnexpectedDatabaseError(s"Run ID $runId was created by an unsupported pipeline."))
      res <- ? <~ processor.patchAndUpdateRunRecordDbo(dbo, user, patches.toList)
    } yield res

    patching.run
  }

}
