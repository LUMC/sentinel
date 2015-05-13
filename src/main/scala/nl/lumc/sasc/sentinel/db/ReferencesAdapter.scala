package nl.lumc.sasc.sentinel.db

import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.Reference

trait ReferencesAdapter extends IndexedCollectionAdapter { this: MongodbConnector =>

  def referenceCollectionName: String = "references"

  private lazy val coll = mongo.db(referenceCollectionName)

  override def createIndices(): Unit = {
    coll.createIndex(MongoDBObject("combinedMd5" -> 1), MongoDBObject("unique" -> true))
    super.createIndices()
  }

  def getOrStoreReference(ref: Reference): Reference =
    coll.findOne(MongoDBObject("combinedMd5" -> ref.combinedMd5)) match {
      case Some(dbo) => grater[Reference].asObject(dbo)
      case None =>
        coll.insert(grater[Reference].asDBObject(ref))
        ref
    }

  def getReferences(maxNumReturn: Option[Int] = None): Seq[Reference] = {
    val qResult = coll
      .find()
      .sort(MongoDBObject("creationTime" -> -1))
      .map { case dbo => grater[Reference].asObject(dbo) }
    maxNumReturn match {
      case None       => qResult.toSeq
      case Some(num)  => qResult.take(num).toSeq
    }
  }

  def getReference(refId: String): Option[Reference] = {
    Try(new ObjectId(refId)) match {
      case Failure(_)   => None
      case Success(qid) => coll
        .findOneByID(qid)
        .collect { case dbo => grater[Reference].asObject(dbo) }
    }
  }
}
