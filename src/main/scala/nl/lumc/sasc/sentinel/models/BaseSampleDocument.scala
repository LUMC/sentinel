package nl.lumc.sasc.sentinel.models

abstract class BaseSampleDocument {

  def name: Option[String]

  def runId: String

  def libs: Seq[BaseLibDocument]

  def inputFiles: Seq[BaseFileDocument] = libs.map(_.inputFiles).flatten
}
