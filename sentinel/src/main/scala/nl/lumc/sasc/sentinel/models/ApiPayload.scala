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

import org.scalatra._

import nl.lumc.sasc.sentinel._
import nl.lumc.sasc.sentinel.settings.MaxRunSummarySizeMb

/**
 * Message sent to users interacting with any HTTP endpoint.
 *
 * @param message Main message to send.
 * @param hints Additional information.
 */
sealed case class ApiPayload(message: String, hints: List[String] = List.empty[String])

/** Common API messages. */
object CommonMessages {

  object DuplicateSummaryError {
    def message = "Run summary already uploaded."
    def apply(existingId: String) = ApiPayload(message, hints = List(s"Existing ID: $existingId."))
  }

  object UnexpectedDatabaseError {
    def message = "Unexpected database error."
    def apply(hint: String) = ApiPayload(message, hints = List(hint))
    def apply() = ApiPayload(message)
  }

  val AlreadyUploaded = ApiPayload("Run summary already uploaded.")

  trait ValidationErrorLike {
    def message: String
    final def apply(validationMessages: Seq[String]) = ApiPayload(message, validationMessages.toList)
    final def apply(validationMessage: String) = ApiPayload(message, List(validationMessage))
  }

  object JsonValidationError extends ValidationErrorLike {
    def message = "JSON is invalid."
  }

  object PatchValidationError extends ValidationErrorLike {
    def message = "Invalid patch operation(s)."
  }

  object InvalidDbError {
    def message = "Invalid ID(s) provided."
    def apply(invalidIds: List[String]) = ApiPayload(message, invalidIds)
  }

  object InvalidPipelineError {
    def message = "Pipeline parameter is invalid."
    def apply(validList: Seq[String]) = ApiPayload(message,
      List("Valid values are " + validList.sorted.mkString(", ") + "."))
  }

  val InvalidLibError = ApiPayload("Library type parameter is invalid.",
    List("Valid values are '" + LibType.values.toList.map(_.toString).sorted.mkString("', '") + "'."))

  val InvalidAccLevelError = ApiPayload("Accumulation level parameter is invalid.",
    List("Valid values are '" + AccLevel.values.toList.map(_.toString).sorted.mkString("', '") + "'."))

  val InvalidSeqQcPhaseError = ApiPayload("Sequencing QC phase parameter is invalid.",
    List("Valid values are '" + SeqQcPhase.values.toList.map(_.toString).sorted.mkString("', '") + "'."))

  val ResourceGoneError = ApiPayload("Resource already deleted.")

  val IncompleteDeletionError = ApiPayload("Unexpected database error: deletion incomplete.")

  val UnspecifiedUserId = ApiPayload("User ID not specified.")

  val UnspecifiedRunId = ApiPayload("Run summary ID not specified.")

  val UnspecifiedPipeline = ApiPayload("Pipeline not specified.")

  object MissingUserId {
    def message = "User ID can not be found."
    def apply(missingId: String) = ApiPayload(message, hints = List(s"ID '$missingId' does not exist."))
    def apply() = ApiPayload(message)
  }

  val MissingRunId = ApiPayload("Run summary ID can not be found.")

  val MissingDataPoints = ApiPayload("No data points for aggregation found.")

  val Unauthenticated = ApiPayload("Authentication required to access resource.")

  val UnauthenticatedOptional = ApiPayload("User ID and/or API key is provided but authentication failed.")

  val Unauthorized = ApiPayload("Unauthorized to access resource.")

  val IncorrectAuthMode = ApiPayload("Incorrect authentication mode.")

  val RunSummaryTooLarge = ApiPayload(s"Run summary exceeded maximum allowed size of $MaxRunSummarySizeMb MB.")

  val Unexpected = ApiPayload("Unexpected error.", hints = List("Please contact the site administrators."))
}
