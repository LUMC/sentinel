package nl.lumc.sasc.sentinel.db

import java.io.InputStream
import scala.util.Try

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models.RunRecord

trait RunsAdapter extends IndexedCollectionAdapter { this: MongodbConnector =>

  def runsCollectionName: String

  def processRun(fi: FileItem, userId: String, pipeline: String): Try[RunRecord]

  private lazy val coll = mongo.db(runsCollectionName)

  def storeFile(ins: InputStream, fileName: String, unzipped: Boolean): DbId = {
    mongo.gridfs(ins) { f =>
      f.filename = fileName
      f.contentType = "application/json"
      f.metaData = MongoDBObject("inputGzipped" -> unzipped)
    }.get.toString
  }

  def storeRun(run: RunRecord): WriteResult = {
    val dbo = grater[RunRecord].asDBObject(run)
    coll.insert(dbo)
  }
}
