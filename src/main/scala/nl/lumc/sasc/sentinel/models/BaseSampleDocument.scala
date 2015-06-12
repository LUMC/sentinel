package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

@Salat abstract class BaseSampleDocument {

  def uploaderId: String

  def runName: Option[String]

  def sampleName: Option[String]

  def runId: ObjectId

  def libs: Seq[BaseLibDocument]

  def creationTimeUtc: Date
}
