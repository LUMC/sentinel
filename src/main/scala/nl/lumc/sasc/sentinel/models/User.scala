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

import java.util.Date
import javax.crypto.KeyGenerator

import org.apache.commons.codec.binary.Base64
import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

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

object User {

  /** KeyGen for generating API keys. */
  private val KeyGen = {
    val k = KeyGenerator.getInstance("HmacSHA1")
    k.init(192)
    k
  }

  /** Generates a random string for API keys. */
  def generateApiKey(): String = new String(Base64.encodeBase64(KeyGen.generateKey().getEncoded))

  /** Hashes the given password string. */
  def hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

  object Validator {

    lazy val HasUpperCase = "[A-Z]+".r

    lazy val HasLowerCase = "[a-z]+".r

    lazy val HasNumbers = "[0-9]+".r

    lazy val HasNonWord = """\W+""".r

    /** Regex matchers for user password. */
    lazy val PasswordCheckers = Set(HasUpperCase, HasLowerCase, HasNumbers)

    /** Validation messages for ID validation. */
    def idMessages(id: String): Seq[String] = {
      val msgBuffer = scala.collection.mutable.Buffer.empty[String]
      if (!idLengthValid(id))
        msgBuffer += s"User ID shorter than $MinUserIdLength characters."
      if (!idContentsValid(id))
        msgBuffer += ("User ID contains disallowed characters: '" + invalidIdChars(id).mkString("', '") + "'.")
      msgBuffer.toSeq
    }

    /** Validation messages for password validation. */
    def passwordMessages(password: String, confirmPassword: String): Seq[String] = {
      val msgBuffer = scala.collection.mutable.Buffer.empty[String]
      if (!passwordConfirmed(password, confirmPassword))
        msgBuffer += "Different passwords given."
      if (!passwordLengthValid(password))
        msgBuffer += s"Password shorter than $MinPasswordLength characters."
      if (!passwordMixValid(password))
        msgBuffer += "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
      msgBuffer.toSeq
    }

    /** Validation messages for email validation. */
    def emailMessages(email: String): Seq[String] =
      if (!emailValid(email)) Seq("Email invalid.")
      else Seq()

    /** Checks whether the ID length is valid. */
    def idLengthValid(id: String) = id.length >= MinUserIdLength

    /** Returns all forbidden characters in the given ID string. */
    def invalidIdChars(id: String) = HasNonWord.findAllIn(id).toSeq

    /** Checks whether the ID string contains forbidden characters. */
    def idContentsValid(id: String) = invalidIdChars(id).isEmpty

    /** Checks whether the given passwords match. */
    def passwordConfirmed(password: String, confirmPassword: String) = password == confirmPassword

    /** Checks whether the passwod length is valid. */
    def passwordLengthValid(password: String) = password.length >= MinPasswordLength

    /** Checks whether the password character composition is valid. */
    def passwordMixValid(password: String) = PasswordCheckers.forall(_.findFirstIn(password).isDefined)

    /** Checks whether the email is valid. */
    def emailValid(email: String) = email matches """^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"""
  }
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
  lazy val validationMessages: Seq[String] = User.Validator.idMessages(id) ++
    User.Validator.passwordMessages(password, confirmPassword) ++ User.Validator.emailMessages(email)

  /** User representation of the request. */
  lazy val user: User =
    User(
      id = id,
      email = email,
      hashedPassword = User.hashPassword(password),
      activeKey = User.generateApiKey(),
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
      case ("/password", p: String)         => User.Validator.passwordMessages(p, p)
      case ("/email", e: String)            => User.Validator.emailMessages(e)
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
      case ("/password", p: String)  => user.copy(hashedPassword = User.hashPassword(p))
      case (other, wise) =>
        throw new IllegalArgumentException("Unexpected '" + other + "' value: '" + wise + "'.")
    }
  }
}
