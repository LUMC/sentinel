package nl.lumc.sasc.sentinel

package object models {

  case class ApiError(message: String, data: Any = None)

  case class UserPatch(email: String, isConfirmed: Boolean)

  object CommonErrors {

    val InvalidPipeline = ApiError("Pipeline parameter is invalid.",
      "Valid values are " + AllowedPipelineParams.keySet.mkString(", ") + ".")

    val InvalidLibType = ApiError("Library type parameter is invalid.",
      "Valid values are " + AllowedLibTypeParams.keySet.mkString(", ") + ".")

    val InvalidAccLevel = ApiError("Accumulation level parameter is invalid.",
      "Valid values are " + AllowedAccLevelParams.keySet.mkString(", ") + ".")

    val InvalidSeqQcPhase = ApiError("Sequencing QC phase parameter is invalid.",
      "Valid values are " + AllowedSeqQcPhaseParams.keySet.mkString(", ") + ".")

    val UnspecifiedUserId = ApiError("User ID not specified.")

    val UnspecifiedRunId = ApiError("Run summary ID not specified.")

    val UnspecifiedPipeline = ApiError("Pipeline not specified.")

    val MissingUserId = ApiError("User ID can not be found.")

    val MissingRunId = ApiError("Run summary ID can not be found.")

    val Unauthenticated = ApiError("Authentication required to access resource.")

    val Unauthorized = ApiError("Unauthorized to access resource.")

    val Unexpected = ApiError("Unexpected error. Please contact the site administrators.")
  }
}
