package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

@Salat abstract class BaseRunDocument {

  def runId: ObjectId
  def uploaderId: String
  def pipeline: String
  def nSamples: Int
  def nLibs: Int
  def creationTime: Date
  def deletionTime: Option[Date]
}
