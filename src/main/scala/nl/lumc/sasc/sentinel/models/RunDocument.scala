package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Key
import org.bson.types.ObjectId

case class RunDocument(
  @Key("_id") runId: ObjectId,
  uploaderId: String,
  pipeline: String,
  nSamples: Int,
  nLibs: Int,
  creationTimeUtc: Date,
  sampleIds: Seq[ObjectId] = Seq(),
  refId: Option[ObjectId] = None,
  annotIds: Option[Seq[ObjectId]] = None,
  deletionTimeUtc: Option[Date] = None,
  name: Option[String] = None) extends BaseRunDocument
