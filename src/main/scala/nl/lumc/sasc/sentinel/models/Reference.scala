package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations._
import org.bson.types.ObjectId

case class Reference(
  @Key("_id") refId: ObjectId,
  contigMd5s: Seq[String],
  combinedMd5: String,
  name: Option[String] = None,
  creationTime: Option[Date] = None)
