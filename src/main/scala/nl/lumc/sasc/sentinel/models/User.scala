package nl.lumc.sasc.sentinel.models

import java.util.Date

import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

import nl.lumc.sasc.sentinel.utils.{ generateApiKey, getUtcTimeNow }
import nl.lumc.sasc.sentinel.utils.users.{ Validator, hashPassword }

/**
 * Representation of a user.
 *
 * @param id User ID.
 * @param email User email.
 * @param hashedPassword Hashed user password for authentication.
 * @param activeKey User API key.
 * @param verified Whether the user has been verified or not.
 * @param isAdmin Whether the user is an admin or not.
 * @param creationTimeUtc UTC time when the user entry is created.
 * @param updateTimeUtc UTC time of the most recent update to the user entry.
 */
case class User(
    id: String,
    email: String,
    hashedPassword: String,
    activeKey: String,
    verified: Boolean,
    isAdmin: Boolean,
    creationTimeUtc: Date,
    _id: ObjectId = new ObjectId,
    updateTimeUtc: Option[Date] = None) {

  /** Checks whether the given string matches the user password. */
  def passwordMatches(candidate: String): Boolean = BCrypt.checkpw(candidate, hashedPassword)

  /** Checks whether the given string matches the user API key. */
  def keyMatches(candidate: String): Boolean = candidate == activeKey

  /** Shortcut to creating a [[UserResponse]] object from this user. */
  lazy val toResponse = UserResponse(id, email, verified, activeKey, updateTimeUtc)
}

/**
 * Representation of an HTTP response payload sent to clients requesting user information.
 *
 * This is used when responding to an HTTP request for the user entry. It shows only the necessary information and
 * hides others that may pose security risks when exposed.
 *
 * @param id User ID.
 * @param email User email.
 * @param verified Whether the user has been verified or not.
 * @param activeKey User API key.
 * @param updateTimeUtc UTC time of the most recent update to the user entry.
 */
case class UserResponse(
  id: String,
  email: String,
  verified: Boolean,
  activeKey: String,
  updateTimeUtc: Option[Date])

/**
 * Representation of an HTTP request payload for creating new users.
 *
 * @param id User ID.
 * @param email User email.
 * @param password User password.
 * @param confirmPassword User password, repeated.
 */
case class UserRequest(id: String, email: String, password: String, confirmPassword: String) {

  /** Validation messages. If nonempty, the given data is invalid. */
  lazy val validationMessages: Seq[String] = Validator.idMessages(id) ++
    Validator.passwordMessages(password, confirmPassword) ++ Validator.emailMessages(email)

  /** User representation of the request. */
  lazy val user: User =
    User(
      id = id,
      email = email,
      hashedPassword = hashPassword(password),
      activeKey = generateApiKey(),
      verified = false,
      isAdmin = false,
      creationTimeUtc = getUtcTimeNow,
      _id = new ObjectId)
}

/**
 * Patch operation on a [[User]] object.
 *
 * Patch operations sent to Sentinel are expected to follow [[https://tools.ietf.org/html/rfc6902 RFC6902]].
 *
 * @param op Patch operation to apply. Currently only `replace` is supported.
 * @param path JSON path pointing to patch operation target. Only `/password`, `/path`, and `/verified`.
 * @param value Value of the patch operation.
 */
case class UserPatch(op: String, path: String, value: Any) {

  /** Valid paths for the patch operation. */
  private val validPaths = Set("/password", "/email", "/verified")

  /** Messages to emit when the patch operation is invalid. */
  private val opMessages =
    if (op == "replace") Seq()
    else Seq(s"Invalid operation: '$op'.")

  /** Messages to emit when any part of the patch operation is invalid. */
  lazy val validationMessages: Seq[String] = {
    val msgs = (path, value) match {
      case ("/verified", v: Boolean)        => Seq()
      case ("/password", p: String)         => Validator.passwordMessages(p, p)
      case ("/email", e: String)            => Validator.emailMessages(e)
      case (x, y) if validPaths.contains(x) => Seq(s"Invalid value for path '$x': '$y'.")
      case (p, _)                           => Seq(s"Invalid path: '$p'.")
    }
    opMessages ++ msgs
  }

  /** Applies the patch operation to the given [[User]] object */
  // NOTE: does not have anything to do with `apply` method of this case class' object.
  def apply(user: User): User = {
    require(validationMessages.isEmpty, "Patch object must be valid.")
    (path, value) match {
      case ("/verified", v: Boolean) => user.copy(verified = v)
      case ("/email", e: String)     => user.copy(email = e)
      case ("/password", p: String)  => user.copy(hashedPassword = hashPassword(p))
      case (other, wise) =>
        throw new IllegalArgumentException("Unexpected '" + other + "' value: '" + wise + "'.")
    }
  }
}
