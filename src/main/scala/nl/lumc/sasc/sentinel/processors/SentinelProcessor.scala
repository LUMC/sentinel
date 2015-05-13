package nl.lumc.sasc.sentinel.processors

import java.io.{ InputStream, ByteArrayInputStream }
import scala.util.Try

import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.utils.getByteArray

trait SentinelProcessor {

  implicit class RichFileItem(fi: FileItem) {

    lazy val (byteContents: Array[Byte], inputUnzipped: Boolean) = getByteArray(fi.getInputStream)

    lazy val tryJson: Try[JValue] = Try(parse(new ByteArrayInputStream(byteContents)))

    def byteStream: InputStream = new ByteArrayInputStream(byteContents)
  }

}
