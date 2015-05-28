package nl.lumc.sasc.sentinel.db

import java.io.ByteArrayInputStream
import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.gridfs.GridFSDBFile
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models.{ PipelineRunStats, RunDocument, User }

trait RunsAdapter extends MongodbConnector {

  def runsCollectionName = CollectionNames.Runs

  def processRun(fi: FileItem, user: User, pipeline: String): Try[RunDocument]

  private lazy val coll = mongo.db(runsCollectionName)

  def storeFile(byteContents: Array[Byte], user: User, pipeline: String,
                fileName: String, unzipped: Boolean): ObjectId = {
    mongo.gridfs(new ByteArrayInputStream(byteContents)) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject(
        "uploaderId" -> user.id,
        "pipeline" -> pipeline,
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

      case otherwise => None
    }
  }

  def getRuns(user: User, pipelines: Seq[String], maxNumReturn: Option[Int] = None): Seq[RunDocument] = {
    val query =
      if (pipelines.isEmpty)
        $and("uploaderId" $eq user.id)
      else
        $and("uploaderId" $eq user.id, $or(pipelines.map(pipeline => "pipeline" $eq pipeline)))
    val qResult = coll
      .find(query)
      .sort(MongoDBObject("creationTime" -> -1))
      .map { case dbo => grater[RunDocument].asObject(dbo) }
    maxNumReturn match {
      case None      => qResult.toSeq
      case Some(num) => qResult.take(num).toSeq
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
