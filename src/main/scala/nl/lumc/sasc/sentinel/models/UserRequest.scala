package nl.lumc.sasc.sentinel.models

import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

import nl.lumc.sasc.sentinel.utils.getTimeNow

case class UserRequest(id: String, email: String, password: String, confirmPassword: String) {

  private val hasUpperCase = "[A-Z]+".r

  private val hasLowerCase = "[a-z]+".r

  private val hasNumbers = "[0-9]+".r

  private val passwordCheckers = Set(hasUpperCase, hasLowerCase, hasNumbers)

  lazy val validationMessages: Seq[String] = {
    val msgBuffer = scala.collection.mutable.Buffer.empty[String]
    if (!idLengthValid)
      msgBuffer += "User ID too short."
    if (!passwordConfirmed)
      msgBuffer += "Different passwords given."
    if (!passwordLengthValid)
      msgBuffer += "Password too short."
    if (!passwordMixValid)
      msgBuffer += "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
    if (!emailValid)
      msgBuffer += "Email invalid."
    msgBuffer.toSeq
  }

  def idLengthValid = id.length >= 3

  def passwordConfirmed = password == confirmPassword

  def passwordLengthValid = password.length >= 6

  def passwordMixValid = passwordCheckers.forall(_.findFirstIn(password).isDefined)

  def emailValid = email matches """^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"""

  lazy val user: User =
    User(
      _id = new ObjectId,
      id = id,
      email = email,
      hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()),
      emailVerified = false,
      isAdmin = false,
      creationTime = getTimeNow)
}
