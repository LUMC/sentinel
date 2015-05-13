package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.{ LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.models._

case class GentrapLibDocument(
  name: Option[String],
  rawSeq: GentrapSeqDocument,
  processedSeq: Option[GentrapSeqDocument],
  alnStats: GentrapAlignmentStats) extends BaseLibDocument {

  lazy val rawStats: SeqStats = SeqStats(rawSeq.read1.stats, rawSeq.read2.collect { case r2 => r2.stats })

  lazy val processedStats: Option[SeqStats] =
    processedSeq.collect { case ps =>
      SeqStats(ps.read1.stats, ps.read2.collect { case r2 => r2.stats })
    }

}
