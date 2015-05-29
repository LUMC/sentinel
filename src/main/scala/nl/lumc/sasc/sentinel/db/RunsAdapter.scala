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

  def runsCollectionName = CollectionNames.Runs

  def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value): Try[RunDocument]

  private lazy val coll = mongo.db(runsCollectionName)

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

  def getRun(runId: String, user: User, doDownload: Boolean): Option[Either[RunDocument, GridFSDBFile]] = {
    Try(new ObjectId(runId)) match {
      case Failure(_) => None
      case Success(qid) =>

        if (doDownload) {
          val query = MongoDBObject("_id" -> qid, "metadata.uploaderId" -> user.id)
          mongo.gridfs
            .findOne(query)
            .collect { case gfs => Right(gfs) }
        } else {
          val query = MongoDBObject("_id" -> qid, "uploaderId" -> user.id)
          coll
            .findOne(query)
            .collect { case dbo => Left(grater[RunDocument].asObject(dbo)) }
        }
    }
  }

  def getRuns(user: User, pipelines: Seq[Pipeline.Value], maxNumReturn: Option[Int] = None): Seq[RunDocument] = {
    val query =
      if (pipelines.isEmpty)
        $and("uploaderId" $eq user.id)
      else
        $and("uploaderId" $eq user.id, $or(pipelines.map(pipeline => "pipeline" $eq pipeline.toString)))
    val qResult = coll
      .find(query)
      .sort(MongoDBObject("creationTime" -> -1))
      .map { case dbo => grater[RunDocument].asObject(dbo) }
    maxNumReturn match {
      case None      => qResult.toSeq
      case Some(num) => qResult.take(num).toSeq
    }
  }

  def deleteRun(runId: String, user: User): Option[RunDocument] = {
    Try(new ObjectId(runId)) match {
      case Failure(_) => None
      case Success(rid) =>

        val deletedDoc = coll
          .findOne(MongoDBObject("_id" -> rid, "deletionTime" -> MongoDBObject("$exists" -> true)))
          .map { case dbo => println(dbo); grater[RunDocument].asObject(dbo) }

        if (deletedDoc.isDefined) deletedDoc
        else {
          val toDelete = coll
            .findAndModify(query = MongoDBObject("_id" -> rid, "deletionTime" -> MongoDBObject("$exists" -> false)),
              update = MongoDBObject("$set" -> MongoDBObject("deletionTime" -> getTimeNow)))
            .map { case dbo => grater[RunDocument].asObject(dbo) }
          toDelete
        }
    }
  }

  final def getGlobalRunStats(): Seq[PipelineRunStats] = {
    coll
      .aggregate(List(
        MongoDBObject("$project" ->
          MongoDBObject("_id" -> 0, "pipeline" -> 1, "nSamples" -> 1, "nLibs" -> 1)),
        MongoDBObject("$group" ->
          MongoDBObject(
            "_id" -> "$pipeline",
            "nRuns" -> MongoDBObject("$sum" -> 1),
            "nSamples" -> MongoDBObject("$sum" -> "$nSamples"),
            "nLibs" -> MongoDBObject("$sum" -> "$nLibs")))),
        AggregationOptions(AggregationOptions.CURSOR))
      .map { case pstat => grater[PipelineRunStats].asObject(pstat) }
      .toSeq
  }
}
