package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations._
import org.bson.types.ObjectId

case class Annotation(
  @Key("_id") annotId: ObjectId,
  annotMd5: String,
  extension: Option[String],
  fileName: Option[String] = None,
  creationTime: Option[Date] = None)