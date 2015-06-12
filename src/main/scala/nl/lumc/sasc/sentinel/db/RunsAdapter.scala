package nl.lumc.sasc.sentinel.db

import java.io.ByteArrayInputStream
import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.gridfs.GridFSDBFile
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.Pipeline
import nl.lumc.sasc.sentinel.models.{ PipelineRunStats, RunDocument, User }
import nl.lumc.sasc.sentinel.utils.getTimeNow

trait RunsAdapter extends MongodbConnector {

  def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value): Try[RunDocument]

  private lazy val coll = mongo.db(collectionNames.Runs)

  def storeFile(byteContents: Array[Byte], user: User, pipeline: Pipeline.Value,
                fileName: String, unzipped: Boolean): ObjectId = {
    mongo.gridfs(new ByteArrayInputStream(byteContents)) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject(
        "uploaderId" -> user.id,
        "pipeline" -> pipeline.toString,
        "inputGzipped" -> unzipped
      )
    }.get match {
      case oid: ObjectId => oid
      case otherwise =>
        throw new RuntimeException("Expected ObjectId from storing file, got '" + otherwise.toString + "' instead.")
    }
  }

  def storeRun(run: RunDocument): WriteResult = {
    val dbo = grater[RunDocument].asDBObject(run)
    coll.insert(dbo)
  }

  def getRun(runId: ObjectId, user: User, doDownload: Boolean): Option[Either[RunDocument, GridFSDBFile]] =
    if (doDownload) {
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
        .collect { case dbo => Left(grater[RunDocument].asObject(dbo)) }
    }

  def getRuns(user: User, pipelines: Seq[Pipeline.Value], maxNumReturn: Option[Int] = None): Seq[RunDocument] = {
    val query =
      if (pipelines.isEmpty)
        $and("uploaderId" $eq user.id)
      else
        $and("uploaderId" $eq user.id, $or(pipelines.map(pipeline => "pipeline" $eq pipeline.toString)))
    val qResult = coll
      .find($and(query :: ("deletionTimeUtc" $exists false)))
      .sort(MongoDBObject("creationTimeUtc" -> -1))
      .map { case dbo => grater[RunDocument].asObject(dbo) }
    maxNumReturn match {
      case None      => qResult.toSeq
      case Some(num) => qResult.take(num).toSeq
    }
  }

  def deleteRun(runId: ObjectId, user: User): Option[(RunDocument, Boolean)] = {
    val docDeleted = coll
      .findOne(MongoDBObject("_id" -> runId, "deletionTimeUtc" -> MongoDBObject("$exists" -> true)))
      .map { case dbo => (grater[RunDocument].asObject(dbo), false) }

    if (docDeleted.isDefined) docDeleted
    else {
      val docToDelete = coll
        .findAndModify(query = MongoDBObject("_id" -> runId, "deletionTimeUtc" -> MongoDBObject("$exists" -> false)),
          update = MongoDBObject("$set" -> MongoDBObject("deletionTimeUtc" -> getTimeNow)),
          returnNew = true,
          fields = MongoDBObject.empty, sort = MongoDBObject.empty, remove = false, upsert = false)
        .map { case dbo => (grater[RunDocument].asObject(dbo), true) }
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

  final def getGlobalRunStats(): Seq[PipelineRunStats] =
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
      .map { case pstat => grater[PipelineRunStats].asObject(pstat) }
      .toSeq
}
