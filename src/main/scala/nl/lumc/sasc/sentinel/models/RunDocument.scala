package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Key

case class RunDocument(
  @Key("_id") runId: String,
  uploader: String,
  pipeline: String,
  nSamples: Int,
  nLibs: Int,
  creationTime: Date,
  refId: Option[String] = None,
  annotIds: Option[Seq[String]] = None)
