/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.models

import nl.lumc.sasc.sentinel._
import nl.lumc.sasc.sentinel.settings.MaxRunSummarySizeMb

/**
 * Message sent to users interacting with any HTTP endpoint.
 *
 * @param message Main message to send.
 * @param hint Additional information.
 */
case class ApiMessage(message: String, hint: Any = None)

/** Common API messages. */
object CommonMessages {

  def invalidPipeline(validList: Seq[String]) = ApiMessage("Pipeline parameter is invalid.",
    "Valid values are " + validList.sorted.mkString(", ") + ".")

  val InvalidLibType = ApiMessage("Library type parameter is invalid.",
    "Valid values are '" + LibType.values.toList.map(_.toString).sorted.mkString("', '") + "'.")

  val InvalidAccLevel = ApiMessage("Accumulation level parameter is invalid.",
    "Valid values are '" + AccLevel.values.toList.map(_.toString).sorted.mkString("', '") + "'.")

  val InvalidSeqQcPhase = ApiMessage("Sequencing QC phase parameter is invalid.",
    "Valid values are '" + SeqQcPhase.values.toList.map(_.toString).sorted.mkString("', '") + "'.")

  val InvalidDbId = ApiMessage("Invalid ID(s) provided.")

  val UnspecifiedUserId = ApiMessage("User ID not specified.")

  val UnspecifiedRunId = ApiMessage("Run summary ID not specified.")

  val UnspecifiedPipeline = ApiMessage("Pipeline not specified.")

  val MissingUserId = ApiMessage("User ID can not be found.")

  val MissingRunId = ApiMessage("Run summary ID can not be found.")

  val MissingDataPoints = ApiMessage("No data points for aggregation found.")

  val Unauthenticated = ApiMessage("Authentication required to access resource.")

  val UnauthenticatedOptional = ApiMessage("User ID and/or API key is provided but authentication failed.")

  val Unauthorized = ApiMessage("Unauthorized to access resource.")

  val IncorrectAuthMode = ApiMessage("Incorrect authentication mode.")

  val RunSummaryTooLarge = ApiMessage(s"Run summary exceeded maximum allowed size of $MaxRunSummarySizeMb MB.")

  val Unexpected = ApiMessage("Unexpected error. Please contact the site administrators.")
}
