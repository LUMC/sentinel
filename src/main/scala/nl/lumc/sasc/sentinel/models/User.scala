package nl.lumc.sasc.sentinel.models

import java.util.Date

import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

import nl.lumc.sasc.sentinel.utils.{ generateApiKey, getTimeNow }
import nl.lumc.sasc.sentinel.utils.users.{ Validator, hashPassword }

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

  def passwordMatches(candidate: String): Boolean = BCrypt.checkpw(candidate, hashedPassword)

  // NOTE: This is kept super simple now since it's not yet our priority
  def keyMatches(candidate: String): Boolean = candidate == activeKey

  lazy val toResponse = UserResponse(id, email, verified, activeKey, updateTimeUtc)
}

case class UserResponse(
  id: String,
  email: String,
  verified: Boolean,
  activeKey: String,
  updateTimeUtc: Option[Date])

case class UserRequest(id: String, email: String, password: String, confirmPassword: String) {

  lazy val validationMessages: Seq[String] = Validator.idMessages(id) ++
    Validator.passwordMessages(password, confirmPassword) ++ Validator.emailMessages(email)

  lazy val user: User =
    User(
      id = id,
      email = email,
      hashedPassword = hashPassword(password),
      activeKey = generateApiKey(),
      verified = false,
      isAdmin = false,
      creationTimeUtc = getTimeNow,
      _id = new ObjectId)
}

case class UserPatch(op: String, path: String, value: Any) {

  private val validPaths = Set("/password", "/email", "/verified")

  private val opMessages =
    if (op == "replace") Seq()
    else Seq(s"Invalid operation: '$op'.")

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

  def apply(user: User): User = {
    require(validationMessages.isEmpty, "Patch object must be valid.")
    (path, value) match {
      case ("/verified", v: Boolean) => user.copy(verified = v)
      case ("/email", e: String)     => user.copy(email = e)
      case ("/password", p: String)  => user.copy(hashedPassword = hashPassword(p))
      case (other, wise)             => throw new IllegalArgumentException("Unexpected '" + other + "' value: '" + wise + "'.")
    }
  }
}
