package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations._
import org.bson.types.ObjectId

/**
 * Representation of an annotation file used in a pipeline run.
 *
 * @param annotId Database ID.
 * @param annotMd5 MD5 checksum of the annotation file.
 * @param extension Extension of the annotation file (lower case).
 * @param fileName Name of the annotation file.
 * @param creationTimeUtc UTC time when the annotation record was created.
 */
case class AnnotationRecord(
  @Key("_id") annotId: ObjectId,
  annotMd5: String,
  extension: Option[String],
  fileName: Option[String] = None,
  creationTimeUtc: Option[Date] = None)