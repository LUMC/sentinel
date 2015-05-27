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
  creationTime: Date,
  refId: Option[ObjectId] = None,
  annotIds: Option[Seq[ObjectId]] = None)
