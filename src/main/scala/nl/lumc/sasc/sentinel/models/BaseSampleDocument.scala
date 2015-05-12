package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseSampleDocument {

  def name: Option[String]

  def runId: String

  def libs: Seq[BaseLibDocument]

  def inputFiles: Seq[BaseFileDocument] = libs.map(_.inputFiles).flatten
}
