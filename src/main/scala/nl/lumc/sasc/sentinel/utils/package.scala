package nl.lumc.sasc.sentinel

import java.io.{ File, InputStream, PushbackInputStream }
import java.util.Date
import java.util.zip.GZIPInputStream
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Clock
import scala.io.Source

import org.scalatra.servlet.FileItem

/** General utilities */
package object utils {

  private[this] val GzipMagic = Seq(0x1f, 0x8b)

  def getResourcePath(url: String): String = {
    require(url startsWith "/", "Resource paths must start with '/'")
    Option(getClass.getResource(url)) match {
      case Some(u) => Paths.get(u.toURI).toString
      case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
    }
  }

  def getResourceFile(url: String): File = new File(getResourcePath(url))

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

  object implicits {

    import scala.language.implicitConversions

    implicit class RichFileItem(fi: FileItem) {
      def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
    }
  }
}
