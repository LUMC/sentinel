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

  def storeAnnotations(annots: Seq[Annotation]): Seq[DbId] = {
    val docs = annots
      .map { case annot =>
        val stored = coll.findOne(MongoDBObject("annotMd5" -> annot.annotMd5))
        stored match {
          case Some(dbo) => dbo
          case None =>
            val doc = grater[Annotation].asDBObject(annot)
            coll.insert(doc)
            doc
        }
      }
    docs.map(_._id).flatten.map(_.toString)
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
