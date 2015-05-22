package nl.lumc.sasc.sentinel.api

import scala.util.{ Failure, Success, Try }

import org.scalatra._
import org.scalatra.swagger._
import org.json4s._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._

class UsersController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  protected val applicationDescription: String = "Operations on user data"
  override protected val applicationName: Option[String] = Some("users")

  val users = new UsersAdapter with MongodbConnector { val mongo = self.mongo }

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  // format: OFF
  val usersUserIdPatchOperation = (apiOperation[Unit]("usersUserIdPatch")
    summary "Updates an existing user record."
    parameters (
      pathParam[String]("userId").description("User ID."),
      bodyParam[UserPatch]("patchOp").description("Patch operations to apply."))
    responseMessages (
      StringResponseMessage(204, "User patched successfully."),
      StringResponseMessage(400, "User ID not specified or patch operations invalid."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  patch("/:userId", operation(usersUserIdPatchOperation)) {
    val userId = params.getAs[String]("userId").getOrElse(halt(400, CommonErrors.UnspecifiedUserId))
    // TODO: return 400 if patch operation invalid
    // TODO: return 404 if user ID not found
    // TODO: return 401 if not authenticated
    // TODO: return 403 if unauthorized
    // TODO: return 200 and user record
  }

  // format: OFF
  val usersUserIdGetOperation = (apiOperation[JValue]("usersUserIdGet")
    summary "Retrieves record of the given user ID."
    notes "This endpoint is only available to the particular user and administrators."
    parameters pathParam[String]("userId").description("User ID.")
    responseMessages (
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/:userId", operation(usersUserIdGetOperation)) {
    val userId = params.getAs[String]("userId").getOrElse(halt(400, CommonErrors.UnspecifiedUserId))
    // TODO: return 404 if user ID not found
    // TODO: return 401 if not authenticated
    // TODO: return 403 if unauthorized
    // TODO: return 200 and user record
  }

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
