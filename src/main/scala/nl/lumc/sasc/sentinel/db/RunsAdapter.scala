package nl.lumc.sasc.sentinel.db

import java.io.ByteArrayInputStream
import scala.util.Try

import com.mongodb.casbah.gridfs.GridFSDBFile
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.Pipeline
import nl.lumc.sasc.sentinel.models.{ PipelineStats, RunRecord, User }
import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

/** Trait for processing and storing run summary files to a run records collection */
trait RunsAdapter extends MongodbConnector {

  /**
   * Processes and stores the given uploaded file to the run records collection.
   *
   * @param fi Run summary file uploaded via an HTTP endpoint.
   * @param user Uploader of the run summary file.
   * @param pipeline Enum value representing the pipeline name that generated the run summary file.
   * @return A run record of the uploaded run summary file.
   */
  def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value): Try[RunRecord] // TODO: use Futures instead

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Runs)

  /**
   * Stores the given byte array as an entry in GridFS.
   *
   * This method is meant for storing uploaded run summary files in GridFS.
   *
   * @param byteContents Byte array to store.
   * @param user Uploader of the byte array.
   * @param pipeline Enum value representing the pipeline name.
   * @param fileName Original uploaded file name.
   * @param inputGzipped Whether the input file was gzipped or not.
   * @return GridFS ID of the stored entry.
   */
  def storeFile(byteContents: Array[Byte], user: User, pipeline: Pipeline.Value,
                fileName: String, inputGzipped: Boolean): ObjectId =
    // TODO: use Futures instead
    mongo.gridfs(new ByteArrayInputStream(byteContents)) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject(
        "uploaderId" -> user.id,
        "pipeline" -> pipeline.toString,
        "inputGzipped" -> inputGzipped
      )
    }.get match {
      case oid: ObjectId => oid
      case otherwise =>
        throw new RuntimeException("Expected ObjectId from storing file, got '" + otherwise.toString + "' instead.")
    }

  /**
   * Stores the given run record into the runs collection.
   *
   * @param run Run record to store.
   * @return Result of the store operation.
   */
  def storeRun(run: RunRecord): WriteResult = {
    // TODO: use Futures instead
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
   * @param retrieveFile Whether to retrieve the run record or the stored run summary file.
   * @return Either the run record or the run summary file.
   */
  def getRun(runId: ObjectId, user: User, retrieveFile: Boolean): Option[Either[RunRecord, GridFSDBFile]] =
    // TODO: use Futures instead
    if (retrieveFile) {
      val userCheck =
        if (user.isAdmin) MongoDBObject.empty
        else MongoDBObject("metadata.uploaderId" -> user.id)
      val query = MongoDBObject("_id" -> runId) ++ userCheck
      mongo.gridfs
        .findOne(query)
        .collect { case gfs => Right(gfs) }
    } else {
      val userCheck =
        if (user.isAdmin) MongoDBObject.empty
        else MongoDBObject("uploaderId" -> user.id)
      val query = MongoDBObject("_id" -> runId, "deletionTimeUtc" -> MongoDBObject("$exists" -> false)) ++ userCheck
      coll
        .findOne(query)
        .collect { case dbo => Left(grater[RunRecord].asObject(dbo)) }
    }

  /**
   * Retrieves all run records uploaded by the given user.
   *
   * @param user Run summary files uploader.
   * @param pipelines Pipeline enums. If non-empty, only run records of the pipelines in the sequence will be retrieved.
   * @return Run records.
   */
  def getRuns(user: User, pipelines: Seq[Pipeline.Value]): Seq[RunRecord] = {
    // TODO: use Futures instead
    val query =
      if (pipelines.isEmpty) $and("uploaderId" $eq user.id)
      else $and("uploaderId" $eq user.id, $or(pipelines.map(pipeline => "pipeline" $eq pipeline.toString)))
    coll
      .find($and(query :: ("deletionTimeUtc" $exists false)))
      .sort(MongoDBObject("creationTimeUtc" -> -1))
      .map { case dbo => grater[RunRecord].asObject(dbo) }
      .toSeq
  }

  /**
   * Deletes a run record and its linked entries.
   *
   * When a run record is deleted, the following happens:
   *    - The underlying run summary file is removed from the database.
   *    - All sample documents created from the run summary file is removed from the database.
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
   * using [[getRun]] and will not be returned by [[getRuns]]. For all practical purposes by the user, it is almost
   * as if the run record is not present.
   *
   * @param runId Run ID to remove.
   * @param user User requesting the delete operation. Only the run uploader him/herself or an admin can delete runs.
   * @return Run record with `deletionTimeUtc` attribute and a boolean showing whether the deletion was performed or not.
   */
  def deleteRun(runId: ObjectId, user: User): Option[(RunRecord, Boolean)] = {
    // TODO: use Futures instead
    val userCheck =
      if (user.isAdmin) MongoDBObject.empty
      else MongoDBObject("uploaderId" -> user.id)

    val docDeleted = coll
      .findOne(MongoDBObject("_id" -> runId,
        "deletionTimeUtc" -> MongoDBObject("$exists" -> true)) ++ userCheck)
      .map { case dbo => (grater[RunRecord].asObject(dbo), false) }

    if (docDeleted.isDefined) docDeleted
    else {
      val docToDelete = coll
        .findAndModify(
          query = MongoDBObject("_id" -> runId,
            "deletionTimeUtc" -> MongoDBObject("$exists" -> false)) ++ userCheck,
          update = MongoDBObject("$set" -> MongoDBObject("deletionTimeUtc" -> getUtcTimeNow)),
          returnNew = true,
          fields = MongoDBObject.empty, sort = MongoDBObject.empty, remove = false, upsert = false)
        .map { case dbo => (grater[RunRecord].asObject(dbo), true) }
      docToDelete.foreach {
        case (doc, _) =>
          val collSamples = mongo.db(collectionNames.pipelineSamples(doc.pipeline))
          // remove the GridFS entry
          mongo.gridfs.remove(doc.runId)
          // and all samples linked to this run
          doc.sampleIds.foreach {
            case oid => collSamples.remove(MongoDBObject("_id" -> oid))
          }
      }
      docToDelete
    }
  }

  /**
   * Retrieves general statistics of all uploaded runs.
   *
   * @return Objects containing statistics of each supported pipeline type.
   */
  final def getGlobalRunStats(): Seq[PipelineStats] =
    // TODO: use Futures instead
    coll
      .aggregate(List(
        MongoDBObject("$project" ->
          MongoDBObject("_id" -> 0, "pipeline" -> 1, "nSamples" -> 1, "nLibs" -> 1)),
        MongoDBObject("$group" ->
          MongoDBObject(
            "_id" -> "$pipeline",
            "nRuns" -> MongoDBObject("$sum" -> 1),
            "nSamples" -> MongoDBObject("$sum" -> "$nSamples"),
            "nLibs" -> MongoDBObject("$sum" -> "$nLibs"))),
        MongoDBObject("$sort" -> MongoDBObject("_id" -> 1))),
        AggregationOptions(AggregationOptions.CURSOR))
      .map { case pstat => grater[PipelineStats].asObject(pstat) }
      .toSeq
}
