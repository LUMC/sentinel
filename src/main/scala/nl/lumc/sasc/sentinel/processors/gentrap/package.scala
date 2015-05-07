package nl.lumc.sasc.sentinel.processors

import nl.lumc.sasc.sentinel.models._

package object gentrap {

  case class GentrapAlignmentStats(
    nReads: Long,
    nReadsAligned: Long,
    rateReadsMismatch: Double,
    rateIndel: Double,
    pctChimeras: Option[Double] = None,
    nSingletons: Option[Long] = None,
    maxInsertSize: Option[Long] = None,
    medianInsertSize: Option[Long] = None,
    stdevInsertSize: Option[Double] = None,
    nBasesAligned: Long,
    nBasesUtr: Long,
    nBasesCoding: Long,
    nBasesIntron: Long,
    nBasesIntergenic: Long,
    nBasesRibosomal: Option[Long],
    median5PrimeBias: Double,
    median3PrimeBias: Double,
    normalizedTranscriptCoverage: Seq[Double])

  case class GentrapReadDocument(file: FileDocument, stats: ReadStats) extends BaseReadDocument

  case class GentrapLibDocument(
    name: Option[String],
    rawRead1: GentrapReadDocument,
    processedRead1: Option[GentrapReadDocument] = None,
    rawRead2: Option[GentrapReadDocument] = None,
    processedRead2: Option[GentrapReadDocument] = None,
    alnStats: GentrapAlignmentStats) extends BaseLibDocument

  case class GentrapSampleDocument(
    name: Option[String] = None,
    runId: String,
    referenceId: String,
    annotationIds: Seq[String],
    libs: Seq[GentrapLibDocument],
    alnStats: Option[GentrapAlignmentStats] = None) extends BaseSampleDocument
}
