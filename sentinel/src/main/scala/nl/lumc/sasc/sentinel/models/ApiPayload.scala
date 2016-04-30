/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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

import nl.lumc.sasc.sentinel.settings.MaxRunSummarySizeMb

/**
 * Message sent to users interacting with any HTTP endpoint. This is implemented as a custom case class, where the
 * equality is only defined for the `message` and `hints` attribute.
 *
 * @param message Main message to send.
 * @param hints Additional information.
 * @param httpFunc Function that transforms this payload into a Scalatra HTTP ActionResult object. This enables a given
 *                 ApiPayload to always be associated with a HTTP action.
 */
sealed case class ApiPayload(
    message: String,
    hints: List[String] = List.empty[String],
    httpFunc: ApiPayload => ActionResult = ap => InternalServerError(ap)) {

  // Need to be overridden to ensure hashCode generation is only affected by the first two attributes.
  override def productArity = 2

  // Similar to productArity, we want to ensure this ignores the `httpFunc` attribute.
  override def productIterator = super.productIterator.take(productArity)

  // Custom equality method that ignores the `httpFunc` attribute.
  override def equals(that: Any) = that match {
    case ApiPayload(msg: String, hnts: List[_], _) => msg == message && hnts == hints
    case _                                         => false
  }

  /** Creates an HTTP ActionResult with this payload. */
  lazy val actionResult: ActionResult = httpFunc(this)

  /** HTTP status code of the HTTP action. */
  lazy val actionStatusCode: Int = actionResult.status.code // A workaround since we can't compare ActionResults directly :(.
}

object ApiPayload {
  val hiddenAttributes = Set("httpFunc", "actionResult", "actionStatusCode")
}

/** Common API messages. */
object Payloads {

  object DuplicateSummaryError {
    def message = "Run summary already uploaded."
    def apply(existingId: String) =
      ApiPayload(message, List(s"Existing ID: $existingId."), (ap: ApiPayload) => Conflict(ap))
  }

  object DuplicateUserIdError {
    def message = "User ID already taken."
    def apply(existingId: String) =
      ApiPayload(message, List(s"Existing ID: $existingId."), (ap: ApiPayload) => Conflict(ap))
  }

  object UnexpectedDatabaseError {
    def message = "Unexpected database error."
    def apply(hint: String) = ApiPayload(message, hints = List(hint))
    def apply(hints: List[String]) = ApiPayload(message, hints = hints)
    def apply() = ApiPayload(message)
  }

  val IncompleteDeletionError = UnexpectedDatabaseError("Deletion incomplete.")

  trait ValidationErrorLike {
    def message: String
    protected val func = (ap: ApiPayload) => BadRequest(ap)
    final def apply(validationMessages: Seq[String]) = ApiPayload(message, validationMessages.toList, func)
    final def apply(validationMessage: String) = ApiPayload(message, List(validationMessage), func)
    final def apply() = ApiPayload(message, httpFunc = func)
  }

  object JsonValidationError extends ValidationErrorLike {
    def message = "JSON is invalid."
  }

  object PatchValidationError extends ValidationErrorLike {
    def message = "Invalid patch operation(s)."
    def apply(patch: SinglePathPatch) = ApiPayload(message,
      List(s"Unexpected operation '${patch.op}' on '${patch.path}' with value '${patch.value}'."))
  }

  object InvalidDbError {
    def message = "Invalid ID(s) provided."
    def apply(invalidIds: List[String]) = ApiPayload(message, invalidIds, (ap: ApiPayload) => BadRequest(ap))
  }

  object InvalidPipelineError {
    def message = "Pipeline parameter is invalid."
    def apply(validList: Seq[String]) = ApiPayload(message,
      List("Valid values are " + validList.sorted.mkString(", ") + "."),
      (ap: ApiPayload) => BadRequest(ap))
  }

  val InvalidLibError = ApiPayload("Library type parameter is invalid.",
    List("Valid values are '" + LibType.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap))

  val InvalidAccLevelError = ApiPayload("Accumulation level parameter is invalid.",
    List("Valid values are '" + AccLevel.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap))

  val InvalidSeqQcPhaseError = ApiPayload("Sequencing QC phase parameter is invalid.",
    List("Valid values are '" + SeqQcPhase.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap))

  val ResourceGoneError = ApiPayload("Resource already deleted.", httpFunc = (ap: ApiPayload) => Gone(ap))

  val UnspecifiedUserIdError = ApiPayload("User ID not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  val UnspecifiedRunIdError = ApiPayload("Run summary ID not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  val UnspecifiedPipelineError = ApiPayload("Pipeline not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  object UserIdNotFoundError {
    protected def func = (ap: ApiPayload) => NotFound(ap)
    def message = "User ID can not be found."
    def apply(missingId: String) = ApiPayload(message, List(s"ID '$missingId' does not exist."), func)
    def apply() = ApiPayload(message, httpFunc = func)
  }

  val RunIdNotFoundError = ApiPayload("Run summary ID can not be found.", httpFunc = (ap: ApiPayload) => NotFound(ap))

  val DataPointsNotFoundError = ApiPayload("No data points for aggregation found.", httpFunc = (ap: ApiPayload) => NotFound(ap))

  val AuthenticationError = ApiPayload("Authentication required to access resource.",
    httpFunc = (ap: ApiPayload) => Unauthorized(ap))

  val OptionalAuthenticationError = ApiPayload("User ID and/or API key is provided but authentication failed.",
    httpFunc = (ap: ApiPayload) => Unauthorized(ap))

  val AuthorizationError = ApiPayload("Unauthorized to access resource.", httpFunc = (ap: ApiPayload) => Forbidden(ap))

  val RunSummaryTooLargeError =
    ApiPayload(s"Run summary exceeded maximum allowed size of $MaxRunSummarySizeMb MB.",
      httpFunc = (ap: ApiPayload) => RequestEntityTooLarge(ap))

  val UnexpectedError = ApiPayload("Unexpected error.", hints = List("Please contact the site administrators."))
}
