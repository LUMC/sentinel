package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models.AggrStat

case class DataPointNames(runName: Option[String], sampleName: Option[String], libName: Option[String])

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
  names: Option[DataPointNames] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
case class GentrapAlignmentAggregateStats(
  nReads: AggrStat,
  nReadsAligned: AggrStat,
  rateReadsMismatch: AggrStat,
  rateIndel: AggrStat,
  nBasesAligned: AggrStat,
  nBasesUtr: AggrStat,
  nBasesCoding: AggrStat,
  nBasesIntron: AggrStat,
  nBasesIntergenic: AggrStat,
  median5PrimeBias: AggrStat,
  median3PrimeBias: AggrStat,
  nBasesRibosomal: Option[AggrStat] = None,
  pctChimeras: Option[AggrStat] = None,
  nSingletons: Option[AggrStat] = None,
  maxInsertSize: Option[AggrStat] = None,
  medianInsertSize: Option[AggrStat] = None,
  stdevInsertSize: Option[AggrStat] = None)
