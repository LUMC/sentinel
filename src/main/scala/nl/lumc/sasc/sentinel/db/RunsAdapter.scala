package nl.lumc.sasc.sentinel.db

import java.io.InputStream
import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.gridfs.GridFSDBFile
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models.{ PipelineRunStats, RunDocument }

trait RunsAdapter extends IndexedCollectionAdapter { this: MongodbConnector =>

  def runsCollectionName: String = "runs"

  def processRun(fi: FileItem, userId: String, pipeline: String): Try[RunDocument]

  override def createIndices() = {
    coll.createIndex(MongoDBObject("md5" -> 1, "metadata.userId" -> 1), MongoDBObject("unique" -> true))
    super.createIndices()
  }

  private lazy val coll = mongo.db(runsCollectionName)

  def storeFile(ins: InputStream, userId: String, pipeline: String, fileName: String, unzipped: Boolean): ObjectId = {
    mongo.gridfs(ins) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject(
        "userId"  -> userId,
        "pipeline" -> pipeline,
        "inputGzipped" -> unzipped
      )
    }.get match {
      case oid: ObjectId  => oid
      case otherwise      =>
        throw new RuntimeException("Expected ObjectId from storing file, got '" + otherwise.toString + "' instead.")
    }
  }

  def storeRun(run: RunDocument): WriteResult = {
    val dbo = grater[RunDocument].asDBObject(run)
    coll.insert(dbo)
  }

  def getRun(runId: String, doDownload: Boolean): Option[Either[RunDocument, GridFSDBFile]] = {
    Try(new ObjectId(runId)) match {
      case Failure(_)   => None
      case Success(qid) =>

        if (doDownload) mongo.gridfs
          .findOne(qid)
          .collect { case gfs => Right(gfs) }
        else coll
          .findOneByID(qid)
          .collect { case dbo => Left(grater[RunDocument].asObject(dbo)) }
    }
  }

  def getRuns(userId: String, pipelines: Seq[String], maxNumReturn: Option[Int] = None): Seq[RunDocument] = {
    val query =
      if (pipelines.isEmpty)
        $and("uploader" $eq userId)
      else
        $and("uploader" $eq userId, $or(pipelines.map(pipeline => "pipeline" $eq pipeline)))
    val qResult = coll
      .find(query)
      .sort(MongoDBObject("creationTime" -> -1))
      .map { case dbo => grater[RunDocument].asObject(dbo) }
    maxNumReturn match {
      case None       => qResult.toSeq
      case Some(num)  => qResult.take(num).toSeq
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
