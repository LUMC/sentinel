package nl.lumc.sasc.sentinel.db

import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.Reference

trait ReferencesAdapter extends MongodbConnector {

  private lazy val coll = mongo.db(collectionNames.References)

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
      .sort(MongoDBObject("creationTimeUtc" -> -1))
      .map { case dbo => grater[Reference].asObject(dbo) }
    maxNumReturn match {
      case None      => qResult.toSeq
      case Some(num) => qResult.take(num).toSeq
    }
  }

  def getReference(refId: ObjectId): Option[Reference] =
    coll
      .findOneByID(refId)
      .collect { case dbo => grater[Reference].asObject(dbo) }
}
