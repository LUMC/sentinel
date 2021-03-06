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
    message:  String,
    hints:    List[String]               = List.empty[String],
    httpFunc: ApiPayload => ActionResult = ap => InternalServerError(ap)
) {

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

  /** Returned when a run summary is already uploaded, HTTP 409. */
  object DuplicateSummaryError {
    def message = "Run summary already uploaded."
    def apply(existingId: String) =
      ApiPayload(message, List(s"Existing ID: $existingId."), (ap: ApiPayload) => Conflict(ap))
  }

  /** Returned when a user ID is already taken, HTTP 409. */
  object DuplicateUserIdError {
    def message = "User ID already taken."
    def apply(existingId: String) =
      ApiPayload(message, List(s"Existing ID: $existingId."), (ap: ApiPayload) => Conflict(ap))
  }

  /** Returned for any generic database error, HTTP 500. */
  object UnexpectedDatabaseError {
    def message = "Unexpected database error."
    def apply(hint: String) = ApiPayload(message, hints = List(hint))
    def apply(hints: List[String]) = ApiPayload(message, hints = hints)
    def apply() = ApiPayload(message)
  }

  /** Returned when a run summary deletion is incomplete, HTTP 500. */
  val IncompleteDeletionError = UnexpectedDatabaseError("Deletion incomplete.")

  /** Trait for validation errors from payloads sent by the user. */
  trait PayloadError {
    def message: String
    protected val func: ApiPayload => ActionResult
    final def apply(validationMessages: Seq[String]) = ApiPayload(message, validationMessages.toList, func)
    final def apply(validationMessage: String) = ApiPayload(message, List(validationMessage), func)
    final def apply() = ApiPayload(message, httpFunc = func)
  }

  /** Returned when the payload is either empty or contains syntax errors. */
  object SyntaxError extends PayloadError {
    def message = "Payload can not be understood."
    protected val func = (ap: ApiPayload) => BadRequest(ap)
  }

  /** Returned when a JSON validation error (or semantic error) occurs. */
  object JsonValidationError extends PayloadError {
    protected val func = (ap: ApiPayload) => UnprocessableEntity(ap)
    def message = "JSON is invalid."
  }

  /** Returned when a JSON patch validation error occurs. */
  object PatchValidationError extends PayloadError {
    protected val func = (ap: ApiPayload) => UnprocessableEntity(ap)
    def message = "Invalid patch operation(s)."
    def apply(patch: JsonPatch.PatchOp) = patch match {

      case p: JsonPatch.PatchOpWithValue => ApiPayload(
        message,
        List(s"Unsupported patch operation and/or value: '${p.op}' on '${p.path}' with value '${p.value}'."), func
      )

      case p: JsonPatch.PatchOpWithFrom => ApiPayload(
        message,
        List(s"Unsupported patch operation: '${p.op}' on '${p.path}' from '${p.from}'."), func
      )

      case otherwise => ApiPayload(
        message,
        List(s"Unsupported patch operation: '${otherwise.op}' on '${otherwise.path}'."), func
      )
    }

  }

  /** Returned when an invalid database ID is specified. HTTP 400. */
  object InvalidDbError {
    def message = "Invalid ID(s) provided."
    def apply(invalidIds: List[String]) = ApiPayload(message, invalidIds, (ap: ApiPayload) => BadRequest(ap))
  }

  /** Returned when an invalid pipeline name is specified. HTTP 400. */
  object InvalidPipelineError {
    def message = "Pipeline parameter is invalid."
    def apply(validList: Seq[String]) = ApiPayload(
      message,
      List("Valid values are " + validList.sorted.mkString(", ") + "."),
      (ap: ApiPayload) => BadRequest(ap)
    )
  }

  /** Returned when an invalid library parameter is specified. HTTP 400. */
  val InvalidLibError = ApiPayload(
    "Library type parameter is invalid.",
    List("Valid values are '" + LibType.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap)
  )

  /** Returned when an invalid accumulation level parameter is specified. HTTP 400. */
  val InvalidAccLevelError = ApiPayload(
    "Accumulation level parameter is invalid.",
    List("Valid values are '" + AccLevel.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap)
  )

  /** Returned when an invalid sequencing QC phase parameter is specified. HTTP 400. */
  val InvalidSeqQcPhaseError = ApiPayload(
    "Sequencing QC phase parameter is invalid.",
    List("Valid values are '" + SeqQcPhase.values.toList.map(_.toString).sorted.mkString("', '") + "'."),
    (ap: ApiPayload) => BadRequest(ap)
  )

  /** Returned when a resource is already deleted. HTTP 410. */
  val ResourceGoneError = ApiPayload("Resource already deleted.", httpFunc = (ap: ApiPayload) => Gone(ap))

  /** Returned when a required user ID URL parameter is not specified. HTTP 400. */
  val UnspecifiedUserIdError = ApiPayload("User ID not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  /** Returned when a required run ID URL parameter is not specified. HTTP 400. */
  val UnspecifiedRunIdError = ApiPayload("Run summary ID not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  /** Returned when a required pipeline name URL parameter is not specified. HTTP 400. */
  val UnspecifiedPipelineError = ApiPayload("Pipeline not specified.", httpFunc = (ap: ApiPayload) => BadRequest(ap))

  /** Returned when a specified user ID URL parameter points to a nonexistent user ID. HTTP 404. */
  object UserIdNotFoundError {
    protected def func = (ap: ApiPayload) => NotFound(ap)
    def message = "User ID can not be found."
    def apply(missingId: String) = ApiPayload(message, List(s"ID '$missingId' does not exist."), func)
    def apply() = ApiPayload(message, httpFunc = func)
  }

  /** Returned when a specified run ID URL parameter points to a nonexistent run ID. HTTP 404. */
  val RunIdNotFoundError = ApiPayload("Run summary ID can not be found.", httpFunc = (ap: ApiPayload) => NotFound(ap))

  /** Returned when no data points for aggregation exist. HTTP 404. */
  val DataPointsNotFoundError = ApiPayload("No data points for aggregation found.", httpFunc = (ap: ApiPayload) => NotFound(ap))

  /** Returned when authentication is not provided or is provided but does not match. HTTP 401. */
  val AuthenticationError = ApiPayload(
    "Authentication required to access resource.",
    httpFunc = (ap: ApiPayload) => Unauthorized(ap)
  )

  /** Returned when an authentication is provided but does not match. HTTP 401. */
  val OptionalAuthenticationError = ApiPayload(
    "User ID and/or API key is provided but authentication failed.",
    httpFunc = (ap: ApiPayload) => Unauthorized(ap)
  )

  /** Returned when an authenticated user is known not to have access to the resource. HTTP 403. */
  val AuthorizationError = ApiPayload("Unauthorized to access resource.", httpFunc = (ap: ApiPayload) => Forbidden(ap))

  /** Returned when a run summary object is too large for upload. HTTP 413. */
  object RunSummaryTooLargeError {
    def message = "Run summary exceeded maximum allowed size."
    def apply(maxRunSummarySize: Long) =
      ApiPayload(
        message,
        List(f"Maximum size is ${maxRunSummarySize / (1024 * 1024)}%d MB.", "You may reduce the upload size using gzip."),
        httpFunc = (ap: ApiPayload) => RequestEntityTooLarge(ap)
      )
  }

  /** Returned when any unexpected errors occur. HTTP 500. */
  val UnexpectedError = ApiPayload("Unexpected error.", hints = List("Please contact the site administrators."))
}
