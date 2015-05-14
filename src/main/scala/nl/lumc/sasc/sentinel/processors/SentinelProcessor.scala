package nl.lumc.sasc.sentinel.processors

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.utils.getByteArray

trait SentinelProcessor {

  implicit class RichFileItem(fi: FileItem) {
    def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
  }

}
