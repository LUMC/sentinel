package nl.lumc.sasc.sentinel

import java.util.Date

package object model {

  case class ApiError(message: String, supportEmail: String)

  case class AlignmentReference(contigIds: List[String], refId: String, name: String)

  case class AnnotationFile(annotId: String, extension: String, name: String)

  case class DepotItem(depotId: String, creationTime: Date, pipeline: String, contents: Object)

  case class GentrapAlignmentStats(
    alnType: String, nReads: Long, nReadsAligned: Long, rateReadsMismatch: Double, rateIndel: Double,
    nChimericPairs: Option[Long] = None, nSingletons: Option[Long] = None, maxInsertSize: Option[Long] = None,
    medianInsertSize: Option[Long] = None, meanInsertSize: Option[Double] = None, stdevInsertSize: Option[Double] = None,
    nBasesAligned: Long, nBasesUtr: Long, nBasesCoding: Long, nBasesIntron: Long, nBasesIntergenic: Long,
    nBasesRibosomal: Long, median5PrimeBias: Double, median3PrimeBias: Double, normalizedTranscriptCoverage: List[Double])

  case class GeneralSeqStats(
    seqType: String, hasPair: Boolean, nBases: Long, nBasesA: Long, nBasesT: Long, nBasesG: Long, nBasesC: Long,
    nBasesN: Long, nBasesByQual: List[Long], medianQualByPosition: List[Double], nReads: Long)

  case class GeneralSeqInput(read1: GeneralSeqStats, read2: Option[GeneralSeqStats] = None)
}
