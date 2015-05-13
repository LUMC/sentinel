package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseSeqDocument {

  def read1: BaseReadDocument

  def read2: Option[BaseReadDocument]

  def files: Seq[BaseFileDocument] = read2 match {
    case Some(r2) => Seq(read1.file, r2.file)
    case None     => Seq(read1.file)
  }
}
