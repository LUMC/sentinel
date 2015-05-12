package nl.lumc.sasc.sentinel.db

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import nl.lumc.sasc.sentinel.models.Annotation

trait AnnotationsAdapter extends IndexedCollectionAdapter { this: MongodbConnector =>

  def annotationCollectionName: String = "annotations"

  private lazy val coll = mongo.db(annotationCollectionName)

  override def createIndices(): Unit = {
    coll.createIndex(MongoDBObject("annotMd5" -> 1), MongoDBObject("unique" -> true))
    super.createIndices()
  }

  def getOrStoreAnnotations(annots: Seq[Annotation]): Seq[Annotation] =
    annots
      .map { case annot => coll.findOne(MongoDBObject("annotMd5" -> annot.annotMd5)) match {
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
      case None       => qResult.toSeq
      case Some(num)  => qResult.take(num).toSeq
    }
  }
}
