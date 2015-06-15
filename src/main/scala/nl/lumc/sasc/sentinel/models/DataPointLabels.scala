package nl.lumc.sasc.sentinel.models

import org.bson.types.ObjectId

case class DataPointLabels(
  runId: ObjectId,
  runName: Option[String],
  sampleName: Option[String],
  libName: Option[String])
