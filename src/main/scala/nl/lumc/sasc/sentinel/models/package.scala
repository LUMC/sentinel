package nl.lumc.sasc.sentinel

import java.util.Date

import org.json4s.JValue

package object models {

  case class ApiError(message: String, data: Any = None)

  case class AlignmentReference(contigIds: List[String], refId: String, name: String)

  case class AnnotationFile(annotId: String, extension: String, name: String)

  case class RunSummary(runId: String, uploadTime: Date, updateTime: Date, uploader: String, public: Boolean,
                        pipeline: String, contents: Option[JValue])

  case class RunSummaryPatch(public: Boolean)

  case class GentrapAlignmentStats(
    alnType: String, nReads: Long, nReadsAligned: Long, rateReadsMismatch: Double, rateIndel: Double,
    nChimericPairs: Option[Long] = None, nSingletons: Option[Long] = None, maxInsertSize: Option[Long] = None,
    medianInsertSize: Option[Long] = None, meanInsertSize: Option[Double] = None, stdevInsertSize: Option[Double] = None,
    nBasesAligned: Long, nBasesUtr: Long, nBasesCoding: Long, nBasesIntron: Long, nBasesIntergenic: Long,
    nBasesRibosomal: Long, median5PrimeBias: Double, median3PrimeBias: Double, normalizedTranscriptCoverage: List[Double])

  case class ReadStats(
    seqType: String, hasPair: Boolean, nBases: Long, nBasesA: Long, nBasesT: Long, nBasesG: Long, nBasesC: Long,
    nBasesN: Long, nBasesByQual: List[Long], medianQualByPosition: List[Double], nReads: Long)

  case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None)

  case class PipelineRunStats(nRuns: Int, nSamples: Int, nLibs: Int)

  case class RunStats(gentrap: PipelineRunStats, unknown: PipelineRunStats)
}
