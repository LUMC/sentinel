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
package nl.lumc.sasc.sentinel.processors

import java.io.ByteArrayInputStream

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.Try

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFSDBFile
import com.novus.salat.{ CaseClass => _, _ }
import com.novus.salat.global.{ ctx => SalatContext }
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.CaseClass
import nl.lumc.sasc.sentinel.adapters.FutureAdapter
import nl.lumc.sasc.sentinel.models.{ Payloads, PipelineStats, BaseRunRecord, User }
import nl.lumc.sasc.sentinel.utils._

/**
 * Base class for processing run summary files.
 */
abstract class RunsProcessor(protected val mongo: MongodbAccessObject)
    extends Processor
    with FutureAdapter {

  /** Type alias for the Processor's run record. */
  type RunRecord <: BaseRunRecord with CaseClass

  /** Overridable execution context for this processor. */
  protected def runsProcessorContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = runsProcessorContext

  /**
   * Processes and stores the given uploaded file to the run records collection.
   *
   * @param contents Upload contents as a byte array.
   * @param uploadName File name of the upload.
   * @param uploader Uploader of the run summary file.
   * @return A run record of the uploaded run summary file.
   */
  def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User): Future[Perhaps[RunRecord]]

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Runs)

  /**
   * Stores the given byte array as an entry in GridFS.
   *
   * This method is meant for storing uploaded run summary files in GridFS.
   *
   * @param contents Byte array to store.
   * @param user Uploader of the byte array.
   * @param fileName Original uploaded file name.
   * @return GridFS ID of the stored entry.
   */
  def storeFile(contents: Array[Byte], user: User, fileName: String): Future[Perhaps[ObjectId]] =
    Future {

      /** Helper method to store raw files. */
      def storeSummary() =
        mongo.gridfs.withNewFile(new ByteArrayInputStream(contents)) { f =>
          f.filename = fileName
          f.contentType = "application/json"
          f.metaData = MongoDBObject(
            "uploaderId" -> user.id,
            "pipeline" -> pipelineName)
        }

      Try(storeSummary()) match {

        case scala.util.Failure(f) => f match {

          case dexc: com.mongodb.DuplicateKeyException =>
            mongo.gridfs.find((gfs: GridFSDBFile) => gfs.md5 == calcMd5(contents)) match {
              case Some(gfs) => gfs._id match {
                case Some(oid) => Payloads.DuplicateSummaryError(oid.toString).left
                case None      => Payloads.UnexpectedDatabaseError("File has no ID.").left
              }
              case None => Payloads.UnexpectedDatabaseError("Conflicting duplicate detection.").left
            }

          case otherwise => Payloads.UnexpectedDatabaseError().left
        }

        case scala.util.Success(s) => s match {

          case Some(v) => v match {
            case oid: ObjectId => oid.right
            case otherwise =>
              val hint = s"Expected ObjectId from storing file, got '${otherwise.toString}' instead."
              Payloads.UnexpectedDatabaseError(hint).left
          }
          case None => Payloads.UnexpectedDatabaseError().left
        }
      }
    }

  /**
   * Stores the given run record into the runs collection.
   *
   * @param run Run record to store.
   * @return Result of the store operation.
   */
  def storeRun(run: RunRecord)(implicit m: Manifest[RunRecord]): Future[WriteResult] = Future {
    val dbo = grater[RunRecord].asDBObject(run)
    coll.insert(dbo)
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
  def getRunRecord(runId: ObjectId, user: User)(implicit m: Manifest[RunRecord]): Future[Option[BaseRunRecord]] = {
    val userCheck =
      if (user.isAdmin) MongoDBObject.empty
      else MongoDBObject("uploaderId" -> user.id)

    Future {
      coll
        .findOne(MongoDBObject("_id" -> runId, "deletionTimeUtc" -> MongoDBObject("$exists" -> false)) ++ userCheck)
        .collect { case dbo => grater[BaseRunRecord].asObject(dbo) }
    }
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
   * Retrieves all run records uploaded by the given user.
   *
   * @param user Run summary files uploader.
   * @param pipelineNames Pipeline names. If non-empty, only run records of the pipelines in the sequence will be retrieved.
   * @return Run records.
   */
  def getRuns(user: User, pipelineNames: Seq[String])(implicit m: Manifest[RunRecord]): Future[Seq[BaseRunRecord]] = {
    val recordGrater = grater[BaseRunRecord]
    val query =
      if (pipelineNames.isEmpty) $and("uploaderId" $eq user.id)
      else $and("uploaderId" $eq user.id, $or(pipelineNames.map(pipeline => "pipeline" $eq pipeline)))

    Future {
      coll
        .find($and(query :: ("deletionTimeUtc" $exists false)))
        .sort(MongoDBObject("creationTimeUtc" -> -1))
        .map { case dbo => recordGrater.asObject(dbo) }
        .toSeq
    }
  }

  /**
   * Deletes the raw uploaded file of the given run record.
   *
   * @param record Run record of the raw file to delete.
   * @return
   */
  // TODO: how to get the WriteResult of this operation? The underlying Java driver just hides it :(.
  def deleteRunGridFSEntry(record: RunRecord): Future[Unit] = Future { mongo.gridfs.remove(record.runId) }

  /**
   * Deletes the sample records of the given run record.
   *
   * @param record Run record of the samples to delete.
   * @return
   */
  def deleteRunSamples(record: RunRecord): Future[BulkWriteResult] = Future {
    val samplesColl = mongo.db(collectionNames.pipelineSamples(record.pipeline))
    val deleter = samplesColl.initializeUnorderedBulkOperation
    val query = MongoDBObject("runId" -> record.runId)
    deleter.find(query).remove()
    deleter.execute()
  }

  /**
   * Deletes the read group records of the given run record.
   *
   * @param record Run record of the read groups to delete.
   * @return
   */
  def deleteRunReadGroups(record: RunRecord): Future[BulkWriteResult] = Future {
    val readGroupsColl = mongo.db(collectionNames.pipelineReadGroups(record.pipeline))
    val builder = readGroupsColl.initializeUnorderedBulkOperation
    val query = MongoDBObject("runId" -> record.runId)
    builder.find(query).remove()
    builder.execute()
  }

  /**
   * Deletes a run record and its linked entries.
   *
   * When a run record is deleted, the following happens:
   *    - The underlying run summary file is removed from the database.
   *    - All unit documents created from the run summary file is removed from the database.
   *    - The run record itself is *not* removed from the database, but it is marked with a `deletionTimeUtc` attribute
   *      to mark when the delete request was made.
   *
   * The reason we keep the run record is so that the appropriate HTTP response can be returned. When a deleted run
   * record is requested via an HTTP endpoint, instead of simply returning 404 (not found) we can return 410 (gone).
   * Furthermore, if any of the expected delete operations on the run summary and/or samples fail, we can use the
   * existing run record to perform any cleanups (e.g. by checking if any run summary files and/or samples with a
   * deleted run record ID exist).
   *
   * Still, even though they exist in the database, run records with a `deletionTimeUtc` attribute can not be retrieved
   * using [[getRunRecord]] or [[getRunFile]] and will not be returned by [[getRuns]]. For all practical purposes by
   * the user, it is almost as if the run record is not present.
   *
   * @param runId Run ID to remove.
   * @param user User requesting the delete operation. Only the run uploader him/herself or an admin can delete runs.
   * @return Run record with `deletionTimeUtc` attribute or an enum indicating any deletion errors.
   */
  def deleteRun(runId: ObjectId, user: User)(implicit m: Manifest[RunRecord]): Future[Perhaps[RunRecord]] = {

    val recordGrater = grater[RunRecord]

    val userCheck =
      if (user.isAdmin) MongoDBObject.empty
      else MongoDBObject("uploaderId" -> user.id)

    /** Helper method to retrieve run record to delete. */
    def getExistingRecord(): Future[Perhaps[RunRecord]] = Future {
      val maybeDoc = coll
        .findOne(MongoDBObject("_id" -> runId) ++ userCheck)
        .map { dbo => recordGrater.asObject(dbo) }
      maybeDoc match {
        case Some(doc) => doc.deletionTimeUtc match {
          case Some(_) => Payloads.ResourceGoneError.left
          case None    => doc.right
        }
        case None => Payloads.RunIdNotFoundError.left
      }
    }

    /** Helper method that marks all GridFS deletion errors as incomplete deletion. */
    def deleteGridFS(record: RunRecord): Future[Perhaps[Unit]] = deleteRunGridFSEntry(record)
      .map(_.right)
      .recover { case e: Exception => Payloads.IncompleteDeletionError.left }

    /** Helper method that marks all sample deletion errors as incomplete deletion. */
    def deleteSamples(record: RunRecord): Future[Perhaps[BulkWriteResult]] = deleteRunSamples(record)
      .map(_.right)
      .recover { case e: Exception => Payloads.IncompleteDeletionError.left }

    /** Helper method that marks all read groups deletion errors as incomplete deletion. */
    def deleteReadGroups(record: RunRecord): Future[Perhaps[BulkWriteResult]] = deleteRunReadGroups(record)
      .map(_.right)
      .recover { case e: Exception => Payloads.IncompleteDeletionError.left }

    /** Helper method to mark run document as deleted. */
    def markRecord(): Future[Perhaps[RunRecord]] = Future {
      val doc = coll
        .findAndModify(
          query = MongoDBObject("_id" -> runId,
            "deletionTimeUtc" -> MongoDBObject("$exists" -> false)) ++ userCheck,
          update = MongoDBObject("$set" -> MongoDBObject("deletionTimeUtc" -> utcTimeNow)),
          returnNew = true,
          fields = MongoDBObject.empty, sort = MongoDBObject.empty, remove = false, upsert = false)
        .map { dbo => recordGrater.asObject(dbo) }
      doc match {
        case Some(obj) => obj.right
        case None      => Payloads.IncompleteDeletionError.left
      }
    }

    val result = for {
      record <- EitherT(getExistingRecord())
      _ <- EitherT(deleteGridFS(record))
      _ <- EitherT(deleteSamples(record))
      _ <- EitherT(deleteReadGroups(record))
      markedRecord <- EitherT(markRecord())
    } yield markedRecord

    result.run
  }

  /**
   * Retrieves general statistics of all uploaded runs.
   *
   * @return Objects containing statistics of each supported pipeline type.
   */
  final def getGlobalRunStats(): Future[Seq[PipelineStats]] = Future {
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
      .map { case pstat => statsGrater.asObject(pstat) }
      .toSeq
  }
}
