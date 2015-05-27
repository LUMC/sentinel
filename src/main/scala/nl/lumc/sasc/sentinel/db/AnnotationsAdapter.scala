package nl.lumc.sasc.sentinel.db

import scala.util.{ Failure, Try, Success }

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.Annotation

trait AnnotationsAdapter extends MongodbConnector {

  def annotationCollectionName = CollectionNames.Annotations

  private lazy val coll = mongo.db(annotationCollectionName)

  def getOrStoreAnnotations(annots: Seq[Annotation]): Seq[Annotation] =
    annots
      .map {
        case annot => coll.findOne(MongoDBObject("annotMd5" -> annot.annotMd5)) match {
          case Some(dbo) => annot.copy(annotId = dbo._id.get)
          case None =>
            coll.insert(grater[Annotation].asDBObject(annot))
            annot
        }
      }

  def getAnnotations(maxNumReturn: Option[Int] = None): Seq[Annotation] = {
    val qResult = coll
      .find()
      .sort(MongoDBObject("creationTime" -> -1))
      .map { case dbo => grater[Annotation].asObject(dbo) }
    maxNumReturn match {
      case None      => qResult.toSeq
      case Some(num) => qResult.take(num).toSeq
    }
  }

  def getAnnotation(annotId: String): Option[Annotation] = {
    Try(new ObjectId(annotId)) match {
      case Failure(_) => None
      case Success(qid) => coll
        .findOneByID(qid)
        .collect { case dbo => grater[Annotation].asObject(dbo) }
    }
  }
}
