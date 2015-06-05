package nl.lumc.sasc.sentinel.models

import java.util.Date

import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.utils.{ generateApiKey, getTimeNow }

case class User(
    id: String,
    email: String,
    hashedPassword: String,
    activeKey: String,
    emailVerified: Boolean,
    isAdmin: Boolean,
    creationTimeUtc: Date,
    _id: ObjectId = new ObjectId,
    updateTimeUtc: Option[Date] = None) {

  def passwordMatches(candidate: String): Boolean = BCrypt.checkpw(candidate, hashedPassword)

  // NOTE: This is kept super simple now since it's not yet our priority
  def keyMatches(candidate: String): Boolean = candidate == activeKey
}

case class UserRequest(id: String, email: String, password: String, confirmPassword: String) {

  private val hasUpperCase = "[A-Z]+".r

  private val hasLowerCase = "[a-z]+".r

  private val hasNumbers = "[0-9]+".r

  private val hasNonWord = """\W+""".r

  private val passwordCheckers = Set(hasUpperCase, hasLowerCase, hasNumbers)

  lazy val validationMessages: Seq[String] = {
    val msgBuffer = scala.collection.mutable.Buffer.empty[String]
    if (!idLengthValid)
      msgBuffer += s"User ID shorter than $MinUserIdLength characters."
    if (!idContentsValid)
      msgBuffer += ("User ID contains disallowed characters: '" + invalidIdChars.mkString("', '") + "'.")
    if (!passwordConfirmed)
      msgBuffer += "Different passwords given."
    if (!passwordLengthValid)
      msgBuffer += s"Password shorter than $MinPasswordLength characters."
    if (!passwordMixValid)
      msgBuffer += "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
    if (!emailValid)
      msgBuffer += "Email invalid."
    msgBuffer.toSeq
  }

  def idLengthValid = id.length >= MinUserIdLength

  private lazy val invalidIdChars = hasNonWord.findAllIn(id).toSeq

  def idContentsValid = invalidIdChars.isEmpty

  def passwordConfirmed = password == confirmPassword

  def passwordLengthValid = password.length >= MinPasswordLength

  def passwordMixValid = passwordCheckers.forall(_.findFirstIn(password).isDefined)

  def emailValid = email matches """^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"""

  lazy val user: User =
    User(
      id = id,
      email = email,
      hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()),
      activeKey = generateApiKey(),
      emailVerified = false,
      isAdmin = false,
      creationTimeUtc = getTimeNow,
      _id = new ObjectId)
}

case class UserPatch(email: String, isConfirmed: Boolean)
