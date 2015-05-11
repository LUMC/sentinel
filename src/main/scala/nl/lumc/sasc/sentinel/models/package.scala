package nl.lumc.sasc.sentinel

import java.util.Date

package object models {

  case class ApiError(message: String, data: Any = None)

  case class User(
    id: String,
    email: String,
    isConfirmed: Boolean,
    isAdmin: Boolean,
    nSummaries: Int,
    creationTime: Date,
    updateTime: Date)

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

  case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None)

  case class PipelineRunStats(nRuns: Int, nSamples: Int, nLibs: Int)

  case class RunStats(gentrap: PipelineRunStats, unknown: PipelineRunStats)

  case class ReadStats(
    nBases: Long,
    nBasesA: Long,
    nBasesT: Long,
    nBasesG: Long,
    nBasesC: Long,
    nBasesN: Long,
    nBasesByQual: Seq[Long],
    medianQualByPosition: Seq[Double],
    nReads: Long)

  case class FileDocument(path: String, md5: String) extends BaseFileDocument
}
