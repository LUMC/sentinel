package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseLibDocument {

  def name: Option[String]

  def rawRead1: BaseReadDocument

  def processedRead1: Option[BaseReadDocument]

  def rawRead2: Option[BaseReadDocument]

  def processedRead2: Option[BaseReadDocument]

  def inputFiles: Seq[BaseFileDocument] = rawRead2 match {
    case Some(r2) => Seq(rawRead1.file, r2.file)
    case None     => Seq(rawRead1.file)
  }
}
