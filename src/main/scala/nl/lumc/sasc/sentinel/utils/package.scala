package nl.lumc.sasc.sentinel

import java.io.{ InputStream, PushbackInputStream }
import java.util.Date
import java.util.zip.GZIPInputStream
import java.security.MessageDigest
import java.time.Clock
import javax.crypto.KeyGenerator
import scala.io.Source

import org.apache.commons.codec.binary.Base64

import org.scalatra.servlet.FileItem

/** General utilities */
package object utils {

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

  def getTimeNow: Date = Date.from(Clock.systemUTC().instant)

  val KeyGen = {
    val k = KeyGenerator.getInstance("HmacSHA1")
    k.init(192)
    k
  }

  def generateApiKey(): String = new String(Base64.encodeBase64(KeyGen.generateKey().getEncoded))

  object implicits {

    import scala.language.implicitConversions

    implicit class RichFileItem(fi: FileItem) {
      def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
    }
  }
}
