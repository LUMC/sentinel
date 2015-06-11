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
import nl.lumc.sasc.sentinel.models.RunDocument

/** General utilities */
package object utils {

  import implicits._

  private[this] val GzipMagic = Seq(0x1f, 0x8b)

  def getResourceStream(url: String): InputStream = {
    require(url startsWith "/", "Resource paths must start with '/'")
    Option(getClass.getResourceAsStream(url)) match {
      case Some(s) => s
      case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
    }
  }

  def getResourceBytes = getResourceStream _ andThen getByteArray andThen ((res: (Array[Byte], Boolean)) => res._1)

  def calcSeqMd5(seq: Seq[String]): String = {
    val digest = MessageDigest.getInstance("MD5")
    seq.foreach { case item => digest.update(item.getBytes) }
    digest.digest().map("%02x".format(_)).mkString
  }

  def splitParam(param: Option[String], delimiter: String = ",",
                 fallback: Seq[String] = Seq()): Seq[String] = param match {
    case Some(str) => str.split(delimiter).toSeq
    case None      => fallback
  }

  def getByteArray(is: InputStream): (Array[Byte], Boolean) = {

    def readAll(i: InputStream) = Source.fromInputStream(i).map(_.toByte).toArray

    val pb = new PushbackInputStream(is, GzipMagic.size)
    val inMagic = Seq(pb.read(), pb.read())
    inMagic.reverse.foreach { pb.unread }

    if (inMagic == GzipMagic) (readAll(new GZIPInputStream(pb)), true)
    else (readAll(pb), false)
  }

  def tryMakeObjectId(id: String): Try[ObjectId] = Try(new ObjectId(id))

  def separateObjectIds(strs: Seq[String]): (Seq[ObjectId], Seq[String]) = {
    val (oids, noids) = strs
      .map { case str => (str.getObjectId, str) }
      .partition { case (x, y) => x.isDefined }
    (oids.map(_._1.get), noids.map(_._2))
  }

  def getTimeNow: Date = Date.from(Clock.systemUTC().instant)

  val KeyGen = {
    val k = KeyGenerator.getInstance("HmacSHA1")
    k.init(192)
    k
  }

  def generateApiKey(): String = new String(Base64.encodeBase64(KeyGen.generateKey().getEncoded))

  val RunDocumentSerializer = FieldSerializer[RunDocument](FieldSerializer.ignore("sampleIds"), { case field => field })

  val SentinelJsonFormats = DefaultFormats + new CustomObjectIdSerializer + RunDocumentSerializer

  object users {

    def hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    object Validator {

      lazy val HasUpperCase = "[A-Z]+".r

      lazy val HasLowerCase = "[a-z]+".r

      lazy val HasNumbers = "[0-9]+".r

      lazy val HasNonWord = """\W+""".r

      lazy val PasswordCheckers = Set(HasUpperCase, HasLowerCase, HasNumbers)

      def idMessages(id: String): Seq[String] = {
        val msgBuffer = scala.collection.mutable.Buffer.empty[String]
        if (!idLengthValid(id))
          msgBuffer += s"User ID shorter than $MinUserIdLength characters."
        if (!idContentsValid(id))
          msgBuffer += ("User ID contains disallowed characters: '" + invalidIdChars(id).mkString("', '") + "'.")
        msgBuffer.toSeq
      }

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

      def emailMessages(email: String): Seq[String] = {
        val msgBuffer = scala.collection.mutable.Buffer.empty[String]
        if (!emailValid(email))
          msgBuffer += "Email invalid."
        msgBuffer.toSeq
      }

      def idLengthValid(id: String) = id.length >= MinUserIdLength

      def invalidIdChars(id: String) = HasNonWord.findAllIn(id).toSeq

      def idContentsValid(id: String) = invalidIdChars(id).isEmpty

      def passwordConfirmed(password: String, confirmPassword: String) = password == confirmPassword

      def passwordLengthValid(password: String) = password.length >= MinPasswordLength

      def passwordMixValid(password: String) = PasswordCheckers.forall(_.findFirstIn(password).isDefined)

      def emailValid(email: String) = email matches """^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"""

    }
  }

  object implicits {

    import scala.language.{ higherKinds, implicitConversions }

    implicit class RichFileItem(fi: FileItem) {
      def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
    }

    implicit class DatabaseId(id: String) {
      def getObjectId: Option[ObjectId] = tryMakeObjectId(id).toOption
    }

    implicit class EnumableString(raw: String) {
      def asEnum[T <: Enumeration#Value](enm: Map[String, T]): Option[T] = enm.get(raw)
    }
  }
}
