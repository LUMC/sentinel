package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.{ LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.models._

/**
 * Gentrap library entry.
 *
 * @param alnStats Alignment statistics of the library.
 * @param seqStatsRaw Sequencing statistics of the raw input sequencing files.
 * @param seqStatsProcessed Sequencing statistics of the QC-ed input sequencing files.
 * @param seqFilesRaw Raw input sequencing file entries.
 * @param seqFilesProcessed QC-ed sequencing file entries.
 * @param libName Library name.
 * @param sampleName Name of the sample to which the library belongs to.
 * @param runName Name of the run to which the library belongs to.
 */
case class GentrapLibDocument(
  alnStats: GentrapAlignmentStats,
  seqStatsRaw: SeqStats,
  seqStatsProcessed: Option[SeqStats],
  seqFilesRaw: SeqFiles,
  seqFilesProcessed: Option[SeqFiles],
  libName: Option[String] = None,
  sampleName: Option[String] = None,
  runName: Option[String] = None) extends BaseLibDocument
