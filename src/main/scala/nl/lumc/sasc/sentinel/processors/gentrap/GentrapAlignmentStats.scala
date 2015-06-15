package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models.{ DataPointAggr, DataPointLabels }

case class GentrapAlignmentStats(
  nReads: Long,
  nReadsAligned: Long,
  rateReadsMismatch: Double,
  rateIndel: Double,
  nBasesAligned: Long,
  nBasesUtr: Long,
  nBasesCoding: Long,
  nBasesIntron: Long,
  nBasesIntergenic: Long,
  median5PrimeBias: Double,
  median3PrimeBias: Double,
  normalizedTranscriptCoverage: Seq[Double],
  nBasesRibosomal: Option[Long] = None,
  pctChimeras: Option[Double] = None,
  nSingletons: Option[Long] = None,
  maxInsertSize: Option[Long] = None,
  medianInsertSize: Option[Long] = None,
  stdevInsertSize: Option[Double] = None,
  labels: Option[DataPointLabels] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
case class GentrapAlignmentStatsAggr(
  nReads: DataPointAggr,
  nReadsAligned: DataPointAggr,
  rateReadsMismatch: DataPointAggr,
  rateIndel: DataPointAggr,
  nBasesAligned: DataPointAggr,
  nBasesUtr: DataPointAggr,
  nBasesCoding: DataPointAggr,
  nBasesIntron: DataPointAggr,
  nBasesIntergenic: DataPointAggr,
  median5PrimeBias: DataPointAggr,
  median3PrimeBias: DataPointAggr,
  nBasesRibosomal: Option[DataPointAggr] = None,
  pctChimeras: Option[DataPointAggr] = None,
  nSingletons: Option[DataPointAggr] = None,
  maxInsertSize: Option[DataPointAggr] = None,
  medianInsertSize: Option[DataPointAggr] = None,
  stdevInsertSize: Option[DataPointAggr] = None)
