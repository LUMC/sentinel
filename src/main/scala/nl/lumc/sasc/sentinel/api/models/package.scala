package nl.lumc.sasc.sentinel.api

import java.util.Date

import org.json4s.JValue

import nl.lumc.sasc.sentinel._

package object models {

  case class ApiError(message: String, data: Any = None)

  case class RunSummary(runId: String, uploadTime: Date, uploader: String, pipeline: String, contents: Option[JValue])

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

  case class Reference(refId: String, contigMd5s: List[String], name: String)

  case class Annotation(annotId: String, annotMd5: String, extension: String, name: String)

  case class User(id: String, email: String, isConfirmed: Boolean, isAdmin: Boolean, nSummaries: Int,
                  creationTime: Date, updateTime: Date)

  case class UserPatch(email: String, isConfirmed: Boolean)

  case class UserRequest(id: String, email: String, password: String)

  object CommonErrors {

    val InvalidPipeline = ApiError("Pipeline parameter is invalid.",
      "Valid values are " + AllowedPipelineParams.mkString(", ") + ".")

    val InvalidLibType = ApiError("Library type parameter is invalid.",
      "Valid values are " + AllowedLibTypeParams.mkString(", ") + ".")

    val InvalidAccLevel = ApiError("Accumulation level parameter is invalid.",
      "Valid values are " + AllowedAccLevelParams.mkString(", ") + ".")

    val UnspecifiedUserId = ApiError("User ID not specified.")

    val UnspecifiedRunId = ApiError("Run summary ID not specified.")

    val UnspecifiedPipeline = ApiError("Pipeline not specified.")

    val MissingUserId = ApiError("User ID can not be found.")

    val MissingRunId = ApiError("Run summary ID can not be found.")

    val Unauthenticated = ApiError("Authentication required to access resource.")

    val Unauthorized = ApiError("Unauthorized to access resource.")
  }
}
