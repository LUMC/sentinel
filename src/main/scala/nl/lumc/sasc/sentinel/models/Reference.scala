package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations._

case class Reference(
  @Key("_id") refId: Option[String],
  contigMd5s: Seq[String],
  combinedMd5: String,
  name: Option[String] = None)
