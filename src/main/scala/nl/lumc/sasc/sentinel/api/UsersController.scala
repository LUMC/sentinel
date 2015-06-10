package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.utils.RunValidationException
import nl.lumc.sasc.sentinel.validation.{ RunValidator, ValidationAdapter }

import scala.util.{ Failure, Success, Try }

import org.scalatra._
import org.scalatra.swagger._
import org.json4s._

import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._

class UsersController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with AuthenticationSupport { self =>

  protected val applicationDescription: String = "Operations on user data"
  override protected val applicationName: Option[String] = Some("users")

  val users = new UsersAdapter { val mongo = self.mongo }
  val patchValidator = new ValidationAdapter {
    override val validator: RunValidator = createValidator("/schemas/json_patch.json")
  }

  // format: OFF
  val usersUserIdPatchOperation = (apiOperation[Unit]("usersUserIdPatch")
    summary "Updates an existing user record."
    notes
      """This endpoint is used for updating an existing user record. Operations are defined using a subset of the JSON
        | patch specification. Only the `replace` operation on the following user attributes are supported: `password`,
        | `email`.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userId").description("User ID."),
      bodyParam[Seq[UserPatch]]("ops").description("Patch operations to apply."))
    responseMessages (
      StringResponseMessage(204, "User patched successfully."),
      StringResponseMessage(400, "User ID not specified."),
      StringResponseMessage(400, "Patch document is invalid or malformed."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message),
      StringResponseMessage(422, "Patch operation not supported.")))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  patch("/:userRecordId", operation(usersUserIdPatchOperation)) {

    val userRecordId = params("userRecordId")

    val patchOps = Try(patchValidator.parseAndValidate(request.body.getBytes)) match {
      case Failure(f) =>
        f match {
          case vexc: RunValidationException =>
            halt(400, ApiMessage(vexc.getMessage,
              data = vexc.report.collect { case r => r.toString }))
          case otherwise =>
            halt(500, CommonErrors.Unexpected)
        }
      case Success(jv) =>
        val patches = jv.extractOpt[Seq[UserPatch]] match {
          case Some(ps) if ps.nonEmpty => ps
          case otherwise               => halt(400, ApiMessage("Invalid user patch.", data = "Operations can not be empty."))
        }

        if (patches.exists(_.validationMessages.nonEmpty)) {
          halt(400, ApiMessage("Invalid user patch.",
            data = patches
              .collect { case up: UserPatch if up.validationMessages.nonEmpty => up.validationMessages }
              .flatten))
        } else patches
    }

    val user = basicAuth()

    if (!user.isAdmin && patchOps.exists(p => p.path == "/verified")) halt(403, CommonErrors.Unauthorized)
    else if (userRecordId == user.id || user.isAdmin) {
      users.patchAndUpdateUser(userRecordId, patchOps) match {
        case Some(_)   => NoContent()
        case otherwise => InternalServerError()
      }
    } else halt(403, CommonErrors.Unauthorized)
  }

  // Helper endpoint to capture PATCH request with unspecified user ID
  patch("/?") { halt(400, ApiMessage("User record ID not specified.")) }

  options("/:userId") { response.setHeader("Accept-Patch", formats("json")) }

  // format: OFF
  val usersUserIdGetOperation = (apiOperation[UserResponse]("usersUserIdGet")
    summary "Retrieves record of the given user ID."
    notes "This endpoint is only available to the particular user and administrators."
    parameters (
      pathParam[String]("userRecordId").description("User record ID to return."),
      queryParam[String]("userId").description("User ID."))
    responseMessages (
      StringResponseMessage(400, "User record ID not specified."),
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/:userRecordId", operation(usersUserIdGetOperation)) {
    val userRecordId = params("userRecordId")
    val user = simpleKeyAuth(params => params.get("userId"))
    if (user.isAdmin || user.id == userRecordId) {
      users.getUser(userRecordId) match {
        case Some(u) => Ok(u.toResponse)
        case None    => NotFound(CommonErrors.MissingUserId)
      }
    } else halt(403, CommonErrors.Unauthorized)
  }

  // Helper endpoint to capture GET request with unspecified user ID
  get("/?") { halt(400, ApiMessage("User record ID not specified.")) }

  // format: OFF
  val usersUserIdPostOperation = (apiOperation[ApiMessage]("usersPost")
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

  post("/", operation(usersUserIdPostOperation)) {
    val userRequest = parsedBody.extractOrElse[UserRequest](halt(400, ApiMessage("Malformed user request.")))

    if (userRequest.validationMessages.size > 0)
      BadRequest(ApiMessage("Invalid user request.", data = userRequest.validationMessages))
    else {
      Try(users.userExist(userRequest.id)) match {
        case Failure(_) => InternalServerError(CommonErrors.Unexpected)
        case Success(true)  => Conflict(ApiMessage("User ID already taken."))
        case Success(false)  =>
          Try(users.addUser(userRequest.user)) match {
            case Failure(_) => InternalServerError(CommonErrors.Unexpected)
            case Success(_) => Created(
              ApiMessage("New user created.",
                Map("uri" -> ("/users/" + userRequest.user.id), "apiKey" -> userRequest.user.activeKey)))
          }
      }
    }
  }
}
