package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Key

case class RunRecord(
  @Key("_id") runId: String,
  refId: String,
  annotIds: Seq[String],
  creationTime: Date,
  uploader: String,
  pipeline: String,
  nSamples: Int,
  nLibsPerSample: Seq[Int])
