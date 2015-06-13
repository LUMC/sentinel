package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.{ LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.models._

case class GentrapLibDocument(
  alnStats: GentrapAlignmentStats,
  seqStatsRaw: SeqStats,
  seqStatsProcessed: Option[SeqStats],
  seqFilesRaw: SeqFiles,
  seqFilesProcessed: Option[SeqFiles],
  uploaderId: String,
  libName: Option[String] = None,
  sampleName: Option[String] = None,
  runName: Option[String] = None) extends BaseLibDocument
