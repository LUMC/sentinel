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
package nl.lumc.sasc.sentinel.api

import scala.concurrent.Future

import org.scalatra._
import org.scalatra.swagger._
import org.json4s._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.{ JsonValidationExtractor, MongodbAccessObject }

/**
 * Controller for the `/users` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class UsersController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with FutureSupport
    with AuthenticationSupport { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName: Option[String] = Some("users")

  /** Controller description, shown in the generated Swagger spec */
  protected val applicationDescription: String = "Operations on user data"

  /** Adapter for connecting to users collection */
  private[api] val users = new UsersAdapter { val mongo = self.mongo }

  /** Validator for patch payloads */
  val patchValidator = new JsonValidationExtractor { def jsonSchemaUrls = Seq("/schemas/json_patch.json") }

  /** General error handler for any type of exception. */
  error {
    case exc =>
      contentType = formats("json")
      InternalServerError(Payloads.UnexpectedError)
  }

  options("/?") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "HEAD,POST")
  }

  options("/:userRecordId") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,PATCH")
    response.setHeader("Accept-Patch", formats("json"))
  }

  // format: OFF
  val userRecordIdPatchOp = (apiOperation[Unit]("userRecordIdPatch")
    summary "Updates an existing user record."
    notes
      """This endpoint is used for updating an existing user record. Operations are defined using a subset of the JSON
        | patch specification. Only the `replace` operation on the following user attributes are supported: `password`,
        | `email`.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userRecordId").description("User ID to update."),
      headerParam[String](HeaderApiKey).description("User API key."),
      bodyParam[List[SinglePathPatch]]("body").description("Patch operations to apply."))
    responseMessages (
      StringResponseMessage(204, "User patched successfully."),
      StringResponseMessage(400, "User ID not specified."),
      StringResponseMessage(400, "Patch document is invalid or malformed."),
      StringResponseMessage(401, Payloads.AuthenticationError.message),
      StringResponseMessage(403, Payloads.AuthorizationError.message),
      StringResponseMessage(404, Payloads.UserIdNotFoundError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  patch("/:userRecordId", operation(userRecordIdPatchOp)) {

    logger.info(requestLog)

    val userRecordId = params.getAs[String]("userRecordId")
      .getOrElse(halt(400, ApiPayload("User Record ID not specified.")))
    val user = basicAuth()

    new AsyncResult {
      val is =
        users.extractAndValidatePatches(request.body.getBytes) match {
          case -\/(err) => Future.successful(err.toActionResult)
          case \/-(ops) =>
            users.patchAndUpdateUser(user, userRecordId, ops.toList).map {
              case -\/(err) => err.toActionResult
              case \/-(_)   => NoContent()
            }
        }
    }
  }

  // Helper endpoint to capture PATCH request with unspecified user ID
  patch("/?") { halt(400, ApiPayload("User record ID not specified.")) }

  // format: OFF
  val userRecordIdGetOp = (apiOperation[UserResponse]("userRecordIdGet")
    summary "Retrieves record of the given user ID."
    notes "This endpoint is only available to the particular user and administrators."
    parameters (
      queryParam[String]("userId").description("User ID."),
      headerParam[String](HeaderApiKey).description("User API key."),
      pathParam[String]("userRecordId").description("User record ID to return."))
    responseMessages (
      StringResponseMessage(400, "User record ID not specified."),
      StringResponseMessage(400, Payloads.UnspecifiedUserIdError.message),
      StringResponseMessage(401, Payloads.AuthenticationError.message),
      StringResponseMessage(404, Payloads.UserIdNotFoundError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/:userRecordId", operation(userRecordIdGetOp)) {
    logger.info(requestLog)
    val userRecordId = params("userRecordId")
    val user = simpleKeyAuth(params => params.get("userId"))
    if (user.isAdmin || user.id == userRecordId) new AsyncResult {
      val is =
        users.getUser(userRecordId).map {
          case Some(u) => Ok(u.toResponse)
          case None    => NotFound(Payloads.UserIdNotFoundError)
        }
    }
    else halt(403, Payloads.AuthorizationError)
  }

  // Helper endpoint to capture GET request with unspecified user ID
  get("/?") { halt(400, ApiPayload("User record ID not specified.")) }

  // format: OFF
  val postOp = (apiOperation[ApiPayload]("post")
    summary "Creates a user account."
    notes
      """This endpoint is used for creating new user accounts. The user data must be supplied in the body of the request
        | and formatted as JSON. The JSON payload must define the following keys: `id`, `password`, `confirmPassword`,
        | and `email`. `id` must not contain whitespace, must be at least 3 characters long, and must only contain
        | alphanumeric characters. `password` must be at least 6 characters long and contain a mixture of at least
        | alphanumeric characters with small and large caps. A user can only submit data after the created account is
        | verified (using an out-of-band communication channel).
      """.stripMargin.replaceAll("\n", "")
    responseMessages (
      StringResponseMessage(201, "User record created successfully."),
      StringResponseMessage(400, "User data payload is invalid or userID and/or password requirements not met."),
      StringResponseMessage(409, "User record with the given ID already exists.")))
  // format: OFF

  post("/", operation(postOp)) {
    logger.info(requestLog)
    val userRequest = parsedBody.extractOrElse[UserRequest](halt(400, ApiPayload("Malformed user request.")))

    if (userRequest.validationMessages.nonEmpty)
      BadRequest(ApiPayload("Invalid user request.", hints = userRequest.validationMessages))
    else {
      new AsyncResult {
        val is = users.addUser(userRequest.user).map {
          case -\/(err) => err.toActionResult
          case \/-(wr) =>
            Created(ApiPayload("New user created.",
              List(s"uri: /users/${userRequest.user.id}", s"apiKey: ${userRequest.user.activeKey}")))
        }
      }
    }
  }
}
