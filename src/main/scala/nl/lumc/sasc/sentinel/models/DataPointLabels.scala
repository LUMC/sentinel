package nl.lumc.sasc.sentinel.models

import org.bson.types.ObjectId

/**
 * Data point label.
 *
 * @param runId Database ID of the run in which this data point is contained.
 * @param runName Name of the run in which this data point is contained.
 * @param sampleName Name of the sample in which this data point is contained.
 * @param libName Name of the library in which this data points is contained.
 */
case class DataPointLabels(
  runId: ObjectId,
  runName: Option[String],
  sampleName: Option[String],
  libName: Option[String])
