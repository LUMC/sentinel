package nl.lumc.sasc.sentinel

import java.io.{ InputStream, PushbackInputStream }
import java.util.Date
import java.util.zip.GZIPInputStream
import java.security.MessageDigest
import java.time.Clock
import javax.crypto.KeyGenerator
import scala.io.Source
import scala.util.Try

import org.bson.types.ObjectId
import org.apache.commons.codec.binary.Base64
import org.json4s._
import org.mindrot.jbcrypt.BCrypt
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.models.RunRecord

/** General utilities */
package object utils {

  import implicits._

  private[this] val GzipMagic = Seq(0x1f, 0x8b)

  /**
   * Retrieves a resource as an input stream.
   *
   * @param url URL of the resource.
   * @return an input stream.
   */
  def getResourceStream(url: String): InputStream = Option(getClass.getResourceAsStream(url)) match {
    case Some(s) => s
    case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
  }

  /** Retrieves a resource as a byte array. */
  def getResourceBytes = getResourceStream _ andThen getByteArray andThen ((res: (Array[Byte], Boolean)) => res._1)

  /**
   * Returns the MD5 checksum of the string made by concatenating the given input strings.
   *
   * @param seq Input strings.
   * @return MD5 checksum.
   */
  def calcSeqMd5(seq: Seq[String]): String = {
    val digest = MessageDigest.getInstance("MD5")
    seq.foreach { case item => digest.update(item.getBytes) }
    digest.digest().map("%02x".format(_)).mkString
  }

  /**
   * Splits a raw URL parameter using the given delimiter.
   *
   * @param param Raw URL parameter string.
   * @param delimiter String delimiter.
   * @param fallback Fallback string.
   * @return String items of the raw URL parameter, or the fallback string.
   */
  def splitParam(param: Option[String], delimiter: String = ",",
                 fallback: Seq[String] = Seq()): Seq[String] = param match {
    case Some(str) => str.split(delimiter).toSeq
    case None      => fallback
  }

  /**
   * Transforms the given input stream into a byte array.
   *
   * @param is Input stream.
   * @return A tuple of 2 items: the byte array and a boolean indicating whether the input stream was gzipped or not.
   */
  def getByteArray(is: InputStream): (Array[Byte], Boolean) = {

    def readAll(i: InputStream) = Source.fromInputStream(i).map(_.toByte).toArray

    val pb = new PushbackInputStream(is, GzipMagic.size)
    val inMagic = Seq(pb.read(), pb.read())
    inMagic.reverse.foreach { pb.unread }

    if (inMagic == GzipMagic) (readAll(new GZIPInputStream(pb)), true)
    else (readAll(pb), false)
  }

  /** Tries to make an Object ID from the given string. */
  def tryMakeObjectId(id: String): Try[ObjectId] = Try(new ObjectId(id))

  /**
   * Given a list of strings, separates it into two subsequences: one where the string is transformed into Object IDs
   * and another where the string can not be transformed into Object IDs.
   *
   * @param strs Input strings.
   * @return A tuple of 2 items: Object IDs and strings that can not be transformed to Object IDs.
   */
  def separateObjectIds(strs: Seq[String]): (Seq[ObjectId], Seq[String]) = {
    val (oids, noids) = strs
      .map { case str => (str.getObjectId, str) }
      .partition { case (x, y) => x.isDefined }
    (oids.map(_._1.get), noids.map(_._2))
  }

  /** Gets the current UTC time. */
  def getUtcTimeNow: Date = Date.from(Clock.systemUTC().instant)

  /** KeyGen for generating API keys. */
  val KeyGen = {
    val k = KeyGenerator.getInstance("HmacSHA1")
    k.init(192)
    k
  }

  /** Generates a random string for API keys. */
  def generateApiKey(): String = new String(Base64.encodeBase64(KeyGen.generateKey().getEncoded))

  /** Serializer for outgoing JSON payloads. */
  val RunDocumentSerializer = FieldSerializer[RunRecord](FieldSerializer.ignore("sampleIds"), { case field => field })

  /** JSON format used across the entire package. */
  val SentinelJsonFormats = DefaultFormats + new CustomObjectIdSerializer + RunDocumentSerializer

  object users {

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

  object implicits {

    import scala.language.{ higherKinds, implicitConversions }

    /** Implicit class for adding our custom read function to an uploaded file item. */
    implicit class RichFileItem(fi: FileItem) {
      def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
    }

    /** Implicit class for creating database IDs from raw strings. */
    implicit class DatabaseId(id: String) {
      def getObjectId: Option[ObjectId] = tryMakeObjectId(id).toOption
    }

    /** Implicit class for creating enum values from raw strings. */
    implicit class EnumableString(raw: String) {
      def asEnum[T <: Enumeration#Value](enm: Map[String, T]): Option[T] = enm.get(raw)
    }
  }
}
