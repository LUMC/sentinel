package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

@Salat abstract class BaseSampleDocument {

  def name: Option[String]

  def runId: ObjectId

  def libs: Seq[BaseLibDocument]

  def inputFiles: Seq[BaseFileDocument] = libs.map(_.inputFiles).flatten
}
