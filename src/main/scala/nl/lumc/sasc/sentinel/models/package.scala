package nl.lumc.sasc.sentinel

package object models {

  case class ApiMessage(message: String, data: Any = None)

  case class UserPatch(email: String, isConfirmed: Boolean)

  object CommonErrors {

    val InvalidPipeline = ApiMessage("Pipeline parameter is invalid.",
      "Valid values are " + AllowedPipelineParams.keySet.mkString(", ") + ".")

    val InvalidLibType = ApiMessage("Library type parameter is invalid.",
      "Valid values are " + AllowedLibTypeParams.keySet.mkString(", ") + ".")

    val InvalidAccLevel = ApiMessage("Accumulation level parameter is invalid.",
      "Valid values are " + AllowedAccLevelParams.keySet.mkString(", ") + ".")

    val InvalidSeqQcPhase = ApiMessage("Sequencing QC phase parameter is invalid.",
      "Valid values are " + AllowedSeqQcPhaseParams.keySet.mkString(", ") + ".")

    val UnspecifiedUserId = ApiMessage("User ID not specified.")

    val UnspecifiedRunId = ApiMessage("Run summary ID not specified.")

    val UnspecifiedPipeline = ApiMessage("Pipeline not specified.")

    val MissingUserId = ApiMessage("User ID can not be found.")

    val MissingRunId = ApiMessage("Run summary ID can not be found.")

    val Unauthenticated = ApiMessage("Authentication required to access resource.")

    val Unauthorized = ApiMessage("Unauthorized to access resource.")

    val IncorrectAuthMode = ApiMessage("Incorrect authentication mode.")

    val Unexpected = ApiMessage("Unexpected error. Please contact the site administrators.")
  }
}
