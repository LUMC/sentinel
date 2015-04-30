/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel

import java.io.File
import java.nio.file.Paths

import nl.lumc.sasc.sentinel.models._

/** General utilities */
package object utils {

  def getResourcePath(url: String): String = {
    require(url startsWith "/", "Resource paths must start with '/'")
    Option(getClass.getResource(url)) match {
      case Some(u) => Paths.get(u.toURI).toString
      case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
    }
  }

  def getResourceFile(url: String): File = new File(getResourcePath(url))

  def splitParam(param: Option[String], delimiter: String = ",",
                 fallback: Seq[String] = Seq()): Seq[String] = param match {
    case Some(str)  => str.split(delimiter).toSeq
    case None       => fallback
  }

  object CommonErrors {
    val InvalidPipeline = ApiError("Pipeline parameter is invalid.",
      "Valid values are " + AllowedPipelineParams.mkString(", ") + ".")
    val InvalidLibType = ApiError("Library type parameter is invalid.",
      "Valid values are " + AllowedLibTypeParams.mkString(", ") + ".")
    val InvalidAccLevel = ApiError("Accumulation level parameter is invalid.",
      "Valid values are " + AllowedAccLevelParams.mkString(", ") + ".")
    val UnspecifiedUserId = ApiError("User ID not specified.")
    val UnspecifiedRunId = ApiError("Run summary ID not specified.")
    val MissingUserId = ApiError("User ID can not be found.")
    val MissingRunId = ApiError("Run summary ID can not be found.")
    val Unauthenticated = ApiError("Authentication required to access resource.")
    val Unauthorized = ApiError("Unauthorized to access resource.")
  }
}
