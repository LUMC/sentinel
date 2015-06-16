package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

/** Base class for file entries in [[nl.lumc.sasc.sentinel.models.RunRecord]]. */
@Salat abstract class BaseFileDocument {

  /** File system path of the file. */
  def path: String

  /** MD5 checksum of the file. */
  def md5: String
}

/** Minimal implementation of a file entry. */
case class FileDocument(path: String, md5: String) extends BaseFileDocument
