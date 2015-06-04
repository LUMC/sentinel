package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

@Salat abstract class BaseSampleDocument {

  def runName: Option[String]

  def name: Option[String]

  def runId: ObjectId

  def libs: Seq[BaseLibDocument]
}
