package nl.lumc.sasc.sentinel.adapters

import scala.util.Try

import org.json4s.JValue

import nl.lumc.sasc.sentinel.db.MongodbConnector
import nl.lumc.sasc.sentinel.models.Annotation

trait MultiAnnotationsAdapter { this: MongodbConnector =>

  def annotationCollectionName: String = "annotations"

  def extractAnnotations(runJson: JValue): Seq[Annotation]

  def storeAnnotations(annots: Seq[Annotation]): Try[Seq[DbId]] = scala.util.Success(Seq(""))
}
