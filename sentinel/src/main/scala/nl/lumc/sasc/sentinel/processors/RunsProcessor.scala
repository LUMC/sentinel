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

import java.io.ByteArrayInputStream

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.Try

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFSDBFile
import com.novus.salat.{ CaseClass => _, _ }
import com.novus.salat.global.{ ctx => SalatContext }
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.adapters.{ ReadGroupsAdapter, SamplesAdapter }
import nl.lumc.sasc.sentinel.models.{ SinglePathPatch => SPPatch, _ }
import nl.lumc.sasc.sentinel.models.Payloads._
import nl.lumc.sasc.sentinel.utils._
import nl.lumc.sasc.sentinel.utils.Implicits.RunRecordDBObject

/**
 * Base class for processing run summary files.
 */
abstract class RunsProcessor(protected[processors] val mongo: MongodbAccessObject) extends Processor {

  /** Type alias for the Processor's run record. */
  type RunRecord <: BaseRunRecord with CaseClass

  /**
   * Manifest for the subclass-defined RunRecord.
   *
   * Although in most cases this would be implemented in the subclass as `implicitly[Manifest[{subclass_run_record}]]`,
   * we still need to have the subclass implement that actual code. A concrete implementation at this level would not
   * compile since the compiler can not give a manifest for abstract types.
   */
  def runManifest: Manifest[RunRecord]

  /** Overridable execution context for this processor. */
  protected def runsProcessorContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = runsProcessorContext

  /** Helper class for patching runs. */
  val patcher = RunsPatcher

  /** Default patch functions for run records. */
  def runPatchFuncs: NonEmptyList[DboPatchFunc] = NonEmptyList(RunsProcessor.RunPatch.replaceRunNamePF)

  /** Default patch functions for the samples of a given run. */
  def samplesPatchFuncs: NonEmptyList[DboPatchFunc] = NonEmptyList(RunsProcessor.SamplesPatch.replaceRunNamePF)

  /** Default patch functions for the read groups of a given run. */
  def readGroupsPatchFuncs: NonEmptyList[DboPatchFunc] = NonEmptyList(RunsProcessor.ReadGroupsPatch.replaceRunNamePF)

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

  /** Function for updating run record database objects. */
  private val updateRunDbo = updateDbo(coll) _

  /** Helper method to create required database queries of this runs processor. */
  final def makeBasicDbQuery(runId: ObjectId, user: User) =
    MongoDBObject("_id" -> runId, "pipeline" -> pipelineName) ++ (if (user.isAdmin) MongoDBObject.empty
    else MongoDBObject("uploaderId" -> user.id))

  /**
   * Applies the given patch operations to the given raw run record object.
   *
   * @param dbo Raw run record object to patch.
   * @param user User performing the patch.
   * @param patches Patch operations to apply
   * @return Either error messages or the number of (run, samples, read groups) updated.
   */
  def patchAndUpdateRunRecordDbo(dbo: DBObject, user: User, patches: List[SPPatch]): Future[Perhaps[(Int, Int, Int)]] = {

    val runRecordPatch = for {
      _ <- ? <~ dbo.checkForAccessBy(user)
      patchedRunDbo <- ? <~ patchDbo(dbo, patches) { runPatchFuncs.list.reduceLeft { _ orElse _ } }
      writeResult <- ? <~ updateRunDbo(patchedRunDbo)
    } yield writeResult.getN

    val allUnitsPatch: AsyncPerhaps[(Int, Int, Int)] = this match {

      case rga: ReadGroupsAdapter => for {
        nRunsUpdated <- runRecordPatch
        sampleIds = dbo.sampleIds
        readGroupIds = dbo.readGroupIds
        // Update samples and read groups in parallel
        sUpdate = rga.patchAndUpdateSampleRecords(sampleIds, patches) {
          samplesPatchFuncs.list.reduceLeft { _ orElse _ }
        }
        rgUpdate = rga.patchAndUpdateReadGroupRecords(readGroupIds, patches) {
          readGroupsPatchFuncs.list.reduceLeft { _ orElse _ }
        }
        nSamplesUpdated <- ? <~ sUpdate
        nReadGroupsUpdated <- ? <~ rgUpdate
      } yield (nRunsUpdated, nSamplesUpdated, nReadGroupsUpdated)

      case sa: SamplesAdapter => for {
        nRunsUpdated <- runRecordPatch
        sampleIds = dbo.sampleIds
        nSamplesUpdated <- ? <~ sa.patchAndUpdateSampleRecords(sampleIds, patches) {
          samplesPatchFuncs.list.reduceLeft { _ orElse _ }
        }
      } yield (nRunsUpdated, nSamplesUpdated, 0)

      case otherwise => for {
        nRunsUpdated <- runRecordPatch
      } yield (nRunsUpdated, 0, 0)

    }

    allUnitsPatch.run
  }

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
  def storeRun(run: RunRecord): Future[WriteResult] = Future {
    val dbo = grater[RunRecord](SalatContext, runManifest).asDBObject(run)
    coll.insert(dbo)
  }

  /**
   * Helper method for transforming a given raw run database object into the RunRecord object.
   *
   * @param runDbo The raw run database object.
   * @return RunRecord object of this processor.
   */
  def dbo2RunRecord(runDbo: DBObject): Option[RunRecord] =
    scala.util.Try(grater[RunRecord](SalatContext, runManifest).asObject(runDbo)).toOption

  /**
   * Retrieves a single run record owned by the given user.
   *
   * @param runId ID of the run to retrieve.
   * @param user Run uploader.
   * @param ignoreDeletionStatus Whether to return runs that have been deleted or not.
   * @return Run record, if it exists, or an [[ApiPayload]] object containing the reason why the run can not be returned.
   */
  def getRunRecord(runId: ObjectId, user: User, ignoreDeletionStatus: Boolean = false): Future[Perhaps[RunRecord]] =
    Future {
      coll
        .findOne(makeBasicDbQuery(runId, user))
        .map { dbo => grater[RunRecord](SalatContext, runManifest).asObject(dbo) }
    }.map {
      case Some(rec) => rec.deletionTimeUtc match {
        case Some(_) => if (ignoreDeletionStatus) rec.right else Payloads.ResourceGoneError.left
        case None    => rec.right
      }
      case None => Payloads.RunIdNotFoundError.left
    }

  /**
   * Retrieves all run records uploaded by the given user.
   *
   * @param user Run summary files uploader.
   * @return Run records.
   */
  def getRuns(user: User): Future[Seq[RunRecord]] = {
    val recordGrater = grater[RunRecord](SalatContext, runManifest)
    val userQ = "uploaderId" $eq user.id
    val pipelineQ = "pipeline" $eq pipelineName
    val delQ = "deletionTimeUtc" $exists false

    val query =
      if (user.isAdmin) $and(pipelineQ :: delQ)
      else $and($and(pipelineQ, userQ) :: delQ)

    Future {
      coll
        .find(query)
        .sort(MongoDBObject("creationTimeUtc" -> -1))
        .map { dbo => recordGrater.asObject(dbo) }
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
  protected def deleteUploadedFile(record: RunRecord): Future[Perhaps[Unit]] =
    Future { mongo.gridfs.remove(record.runId).right }
      .recover { case e: Exception => Payloads.IncompleteDeletionError.left }

  /** Marks the record with the given runId as deleted. */
  protected def markRunAsDeleted(runId: ObjectId, user: User): Future[Perhaps[RunRecord]] = Future {
    coll
      .findAndModify(
        query = makeBasicDbQuery(runId, user) ++ MongoDBObject("deletionTimeUtc" -> MongoDBObject("$exists" -> false)),
        update = MongoDBObject("$set" -> MongoDBObject("deletionTimeUtc" -> utcTimeNow)),
        returnNew = true,
        fields = MongoDBObject.empty, sort = MongoDBObject.empty, remove = false, upsert = false)
      .map { dbo => grater[RunRecord](SalatContext, runManifest).asObject(dbo) }
      .toRightDisjunction(Payloads.IncompleteDeletionError)
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
   * using [[getRunRecord]] or [[nl.lumc.sasc.sentinel.processors.CompositeRunsProcessor.getRunFile]] and will not be
   * returned by [[getRuns]]. For all practical purposes by the user, it is almost as if the run record is not present.
   *
   * @param dbo Raw database object of the run to remove.
   * @param user User requesting the delete operation. Only the run uploader him/herself or an admin can delete runs.
   * @return Run record with `deletionTimeUtc` attribute or an enum indicating any deletion errors.
   */
  def deleteRunDbo(dbo: DBObject, user: User): Future[Perhaps[RunRecord]] = {
    val deletion = for {
      _ <- ? <~ dbo.checkForAccessBy(user)
      record <- ? <~ dbo2RunRecord(dbo)
        .toRightDisjunction(UnexpectedDatabaseError("Error when trying to convert raw database entry into an object."))
      _ <- ? <~ (if (record.deletionTimeUtc.nonEmpty) ResourceGoneError.left else ().right)
      // Invoke the deletion methods as value declarations so they can be launched asynchronously instead of waiting
      // for a previous invocation to complete.
      d1 = deleteUploadedFile(record)
      d2 = this match {
        case sa: SamplesAdapter =>
          sa.deleteSamples(record.sampleIds)
            .map(_.right)
            .recover { case e: Exception => IncompleteDeletionError.left }
        case otherwise => Future.successful(().right)
      }
      d3 = this match {
        case rga: ReadGroupsAdapter =>
          rga.deleteReadGroups(record.readGroupIds)
            .map(_.right)
            .recover { case e: Exception => IncompleteDeletionError.left }
        case otherwise => Future.successful(().right)
      }
      _ <- ? <~ d1
      _ <- ? <~ d2
      _ <- ? <~ d3
      markedRecord <- ? <~ markRunAsDeleted(record.runId, user)
    } yield markedRecord

    deletion.run
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
      .map { pstat => statsGrater.asObject(pstat) }
      .toSeq
  }
}

object RunsProcessor {

  object RunPatch {
    /** 'replace' patch for 'runName' in a single run */
    val replaceRunNamePF: DboPatchFunc = {
      case (dbo, SPPatch("replace", "/runName", v: String)) =>
        dbo.getAs[DBObject]("labels") match {
          case Some(ok) =>
            ok.put("runName", v)
            dbo.put("labels", ok)
            dbo.right
          case None => UnexpectedDatabaseError("Required 'labels' not found.").left
        }
    }
  }

  object SamplesPatch {
    /** 'replace' patch for 'runName' in samples */
    val replaceRunNamePF: DboPatchFunc = RunPatch.replaceRunNamePF
  }

  object ReadGroupsPatch {
    /** 'replace' patch for 'runName' in samples */
    val replaceRunNamePF: DboPatchFunc = RunPatch.replaceRunNamePF
  }
}
