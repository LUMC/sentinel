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
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFSDBFile
import com.novus.salat.{ CaseClass => _, _ }
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.adapters.{ ReadGroupsAdapter, SamplesAdapter, UnitsAdapter }
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.JsonPatch._
import nl.lumc.sasc.sentinel.models.Payloads._
import nl.lumc.sasc.sentinel.utils._
import nl.lumc.sasc.sentinel.utils.Implicits.{ ObjectIdFromString, RunRecordDBObject }

/**
 * Base class for processing run summary files.
 */
abstract class RunsProcessor(protected[processors] val mongo: MongodbAccessObject) extends SingularProcessor {

  /** Type alias for the Processor's run record. */
  type RunRecord <: BaseRunRecord with CaseClass

  /**
   * Processes and stores the given uploaded file to the run records collection.
   *
   * @param contents Upload contents as a byte array.
   * @param uploadName File name of the upload.
   * @param uploader Uploader of the run summary file.
   * @return A run record of the uploaded run summary file.
   */
  def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User): Future[Perhaps[RunRecord]]

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

  /** Default patch functions for run records. */
  val runPatchFunc: DboPatchFunction = List(
    RunsProcessor.PatchFunctions.labelsPF,
    UnitsAdapter.dboPatchFunctionDefault).reduceLeft { _ orElse _ }

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Runs)

  /** Function for updating run record database objects. */
  private val updateRunDbo = updateDbo(coll) _

  /** Helper method to create required database queries of this runs processor. */
  final protected def makeBasicDbQuery(runId: ObjectId, user: User): DBObject =
    MongoDBObject("_id" -> runId) ++ makeBasicDbQuery(user)

  /** Helper method to create required database queries of this runs processor. */
  final protected def makeBasicDbQuery(user: User): DBObject =
    MongoDBObject("pipeline" -> pipelineName) ++ (if (user.isAdmin) MongoDBObject.empty
    else MongoDBObject("uploaderId" -> user.id))

  /** Converts the given JsonPatches for the given run ID to UnitPatches objects. */
  protected def jsonPatches2unitPatches(runId: ObjectId,
                                        jsonPatches: List[JsonPatch.PatchOp]): Perhaps[List[UnitPatch.OnUnit]] =
    jsonPatches.traverse[Perhaps, UnitPatch.OnUnit] {

      case p if p.pathTokens.headOption.contains("sampleLabels") =>
        for {
          strId <- p.pathTokens.lift(1)
            .toRightDisjunction(PatchValidationError("'sampleLabels' does not point to any IDs."))
          dbId <- strId.toObjectId
            .toRightDisjunction(PatchValidationError(s"'sampleLabels' targets an invalid ID: '$strId'."))
          updatedPatch <- p.pathTokens.drop(2) match {
            case Nil  => PatchValidationError(s"'sampleLabels' on '$strId' does not have any target attribute.").left
            case vals => p.updatePath("labels" +: vals).right
          }
        } yield UnitPatch.OnSample(dbId, List(updatedPatch))

      case p if p.pathTokens.headOption.contains("readGroupLabels") =>
        for {
          strId <- p.pathTokens.lift(1)
            .toRightDisjunction(PatchValidationError("'readGroupLabels' does not point to any IDs."))
          dbId <- strId.toObjectId
            .toRightDisjunction(PatchValidationError(s"'readGroupLabels' targets an invalid ID: '$strId'."))
          updatedPatch <- p.pathTokens.drop(2) match {
            case Nil  => PatchValidationError(s"'readGroupLabels' on '$strId' does not have any target attribute.").left
            case vals => p.updatePath("labels" +: vals).right
          }
        } yield UnitPatch.OnReadGroup(dbId, List(updatedPatch))

      case otherwise => UnitPatch.OnRun(runId, List(otherwise)).right
    }

  /** Combines the given patches for a database object into a single `UnitPatch.Combined` object. */
  private def combinePatches(dbo: DBObject, patches: List[JsonPatch.PatchOp]): AsyncPerhaps[UnitPatch.Combined] = {

    // Transform json patch objects to unit patch objects
    val unitsPatches = dbo._id
      .toRightDisjunction(UnexpectedDatabaseError("Run record for patching does not have an ID."))
      .flatMap { runId => jsonPatches2unitPatches(runId, patches) }

    for {
      patches <- ? <~ unitsPatches
      // Create the necessary patch objects, e.g. if patching run objects we may also need to patch the subunits
      cpatch <- patches
        .traverse[AsyncPerhaps, UnitPatch.Combined] {

          case p @ UnitPatch.OnRun(_, ops) =>
            val runLevelP = List(p)
            val sampleLevelPs = dbo.sampleIds.map(sid => UnitPatch.OnSample(sid, ops)).toList
            val readGroupLevelPs = dbo.readGroupIds.map(rgid => UnitPatch.OnReadGroup(rgid, ops)).toList
            UnitPatch.Combined(runLevelP, sampleLevelPs, readGroupLevelPs).point[AsyncPerhaps]

          case p @ UnitPatch.OnSample(dbId, ops) =>
            val runLevelP = List.empty[UnitPatch.OnRun]
            val sampleLevelPs = List(p)
            this match {
              case rga: ReadGroupsAdapter => for {
                rgids <- ? <~ rga.getReadGroupIds(Seq(dbId))
                readGroupLevelOps <- ? <~ rgids.map { rgid => UnitPatch.OnReadGroup(rgid, ops) }.toList
              } yield UnitPatch.Combined(runLevelP, sampleLevelPs, readGroupLevelOps)
              case otherwise =>
                UnitPatch.Combined(runLevelP, sampleLevelPs, List.empty[UnitPatch.OnReadGroup]).point[AsyncPerhaps]
            }

          case p @ UnitPatch.OnReadGroup(dbId, _) =>
            val runLevelP = List.empty[UnitPatch.OnRun]
            val sampleLevelPs = List.empty[UnitPatch.OnSample]
            val readGroupLevelPs = List(p)
            UnitPatch.Combined(runLevelP, sampleLevelPs, readGroupLevelPs).point[AsyncPerhaps]
        }
        .map(_.foldLeft(UnitPatch.Combined.empty)((acc, x) => acc ++ x))
    } yield cpatch
  }

  /**
   * Applies the given patch operations to the given raw run record object.
   *
   * @param dbo Raw run record object to patch.
   * @param user User performing the patch.
   * @param patches Patch operations to apply
   * @return Either error messages or the number of (run, samples, read groups) updated.
   */
  def patchAndUpdateRunDbo(dbo: DBObject, user: User, patches: List[JsonPatch.PatchOp]): Future[Perhaps[(Int, Int, Int)]] = {

    // Combine all patches into a single container after ensuring the requesting user has access to the run
    val combinedPatch = for {
      _ <- ? <~ dbo.authorize(user)
      cpatch <- combinePatches(dbo, patches)
    } yield cpatch

    // Patch run records
    val runPatchOps = for {
      cpatch <- combinedPatch
      patchedDbo <- ? <~ patchDbo(dbo, cpatch.runPatchOps) { runPatchFunc }
    } yield patchedDbo

    // Patch sample records
    val samplePatchOps = this match {

      case sa: SamplesAdapter => for {
        cpatch <- combinedPatch
        patchedSamples <- cpatch.samplePatchOps
          .traverse[AsyncPerhaps, Seq[DBObject]] {
            case (sid, spatches) => ? <~ sa.patchSampleDbo(sid, spatches) { sa.samplesPatchFunc }
          }
      } yield patchedSamples.flatten

      case otherwise => Seq.empty[DBObject].point[AsyncPerhaps]
    }

    // Patch read group records
    val readGroupPatchOps = this match {

      case rga: ReadGroupsAdapter => for {
        cpatch <- combinedPatch
        patchedReadGroups <- cpatch.readGroupPatchOps
          .traverse[AsyncPerhaps, Seq[DBObject]] {
            case (rgid, rgpatches) => ? <~ rga.patchReadGroupDbo(rgid, rgpatches) { rga.readGroupsPatchFunc }
          }
      } yield patchedReadGroups.flatten

      case otherwise => Seq.empty[DBObject].point[AsyncPerhaps]
    }

    // Write patched objects back to the database
    val writeOps: AsyncPerhaps[(Int, Int, Int)] = for {
      patchedRunDbo <- runPatchOps
      patchedSampleDbos <- samplePatchOps
      patchedReadGroupDbos <- readGroupPatchOps

      runWrite <- ? <~ updateRunDbo(patchedRunDbo)
      samplesWrite <- ? <~ (this match {
        case sa: SamplesAdapter => sa.updateSampleDbos(patchedSampleDbos)
        case otherwise          => Future.successful(Seq.empty[WriteResult].right[ApiPayload])
      })
      readGroupsWrite <- ? <~ (this match {
        case rga: ReadGroupsAdapter => rga.updateReadGroupDbos(patchedReadGroupDbos)
        case otherwise              => Future.successful(Seq.empty[WriteResult].right[ApiPayload])
      })
    } yield (runWrite.getN, samplesWrite.map(_.getN).sum, readGroupsWrite.map(_.getN).sum)

    writeOps.run
  }

  /**
   * Stores the given byte array as an entry in GridFS.
   *
   * This method is meant for storing uploaded run summary files in GridFS.
   *
   * @param contents Byte array to store.
   * @param user Uploader of the byte array.
   * @param fileName Original uploaded file name.
   * @return GridFS ID of the stored entry or the reason why the method call fails.
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
   * Helper method for transforming a given raw run database object into a RunRecord object.
   *
   * @param runDbo The raw run database object.
   * @return RunRecord object of this processor.
   */
  def dbo2Run(runDbo: DBObject): Option[RunRecord] =
    scala.util.Try(grater[RunRecord](SalatContext, runManifest).asObject(runDbo)).toOption

  /**
   * Retrieves a single run record owned by the given user.
   *
   * @param runId ID of the run to retrieve.
   * @param user Run uploader.
   * @param ignoreDeletionStatus Whether to return runs that have been deleted or not.
   * @return Run record, if it exists, or an [[ApiPayload]] object containing the reason why the run can not be returned.
   */
  def getRun(runId: ObjectId, user: User, ignoreDeletionStatus: Boolean = false,
             retrieveUnitsLabels: Boolean = false): Future[Perhaps[RunRecord]] = {

    val runGrater = (dbo: DBObject) => grater[RunRecord](SalatContext, runManifest).asObject(dbo)

    val retrieval = Future {
      coll
        .findOne(makeBasicDbQuery(runId, user))
        .toRightDisjunction(Payloads.RunIdNotFoundError)
        .flatMap { dbo =>
          dbo.getAs[java.util.Date]("deletionTimeUtc") match {
            case Some(_) => if (ignoreDeletionStatus) dbo.right else Payloads.ResourceGoneError.left
            case None    => dbo.right
          }
        }
    }

    val action =

      if (!retrieveUnitsLabels) for {
        dbo <- ? <~ retrieval
        record = runGrater(dbo)
      } yield record

      else {
        val suInfos = for {
          runDbo <- ? <~ retrieval
          sLabels <- ? <~ (this match {
            case sa: SamplesAdapter => sa.getSamplesLabels(runDbo.sampleIds)
            case otherwise          => Future.successful(Map.empty[String, SampleLabelsLike].right)
          })
          rgLabels <- ? <~ (this match {
            case rga: ReadGroupsAdapter => rga.getReadGroupsLabels(runDbo.readGroupIds)
            case otherwise              => Future.successful(Map.empty[String, ReadGroupLabelsLike].right)
          })
          rdbo = runDbo ++ MongoDBObject("sampleLabels" -> sLabels, "readGroupLabels" -> rgLabels)
        } yield runGrater(rdbo)

        suInfos
      }

    action.run
  }

  /**
   * Retrieves all run records.
   *
   * If the user is an admin, he/she will get all uploaded runs of the pipeline returned. Otherwise, he/she will only
   * get runs he/she uploaded.
   *
   * @param user Run summary files uploader.
   * @return Run records.
   */
  def getRuns(user: User): Future[Seq[RunRecord]] = Future {
    coll
      .find(makeBasicDbQuery(user) ++ MongoDBObject("deletionTimeUtc" -> MongoDBObject("$exists" -> false)))
      .sort(MongoDBObject("creationTimeUtc" -> -1))
      .map { dbo => grater[RunRecord](SalatContext, runManifest).asObject(dbo) }
      .toSeq
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
   * using [[getRun]] or [[nl.lumc.sasc.sentinel.processors.CompositeRunsProcessor.getRunFile]] and will not be
   * returned by [[getRuns]]. For all practical purposes by the user, it is almost as if the run record is not present.
   *
   * @param dbo Raw database object of the run to remove.
   * @param user User requesting the delete operation. Only the run uploader him/herself or an admin can delete runs.
   * @return Run record with `deletionTimeUtc` attribute or an enum indicating any deletion errors.
   */
  def deleteRunDbo(dbo: DBObject, user: User): Future[Perhaps[RunRecord]] = {
    val deletion = for {
      _ <- ? <~ dbo.authorize(user)
      record <- ? <~ dbo2Run(dbo)
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
}

object RunsProcessor {

  object PatchFunctions {

    private val replaceablePaths = Set("/labels/runName")

    /** 'replace' patch for 'runName' in a single run */
    val labelsPF: DboPatchFunction = {
      case (dbo: DBObject, p @ ReplaceOp(path, value: String)) if replaceablePaths.contains(path) =>
        for {
          okLabels <- dbo
            .getAs[DBObject]("labels")
            .toRightDisjunction(UnexpectedDatabaseError("Run record does not have the required 'labels' attribute."))
          _ <- Try(okLabels.put(p.pathTokens(1), value))
            .toOption
            .toRightDisjunction(UnexpectedDatabaseError(s"Can not patch '$path' in run record."))
          _ <- Try(dbo.put("labels", okLabels))
            .toOption
            .toRightDisjunction(UnexpectedDatabaseError("Can not patch 'labels' in run record."))
        } yield dbo
    }
  }
}
