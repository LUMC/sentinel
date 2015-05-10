package nl.lumc.sasc.sentinel.adapters

import scala.util.Try

import org.json4s.JValue

import nl.lumc.sasc.sentinel.db.MongodbConnector
import nl.lumc.sasc.sentinel.models.Reference

trait SingleReferenceAdapter { this: MongodbConnector =>

  def referenceCollectionName: String = "references"

  def extractReference(runJson: JValue): Reference

  def storeReference(ref: Reference): Try[DbId] = scala.util.Success("")
}
