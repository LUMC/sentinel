package nl.lumc.sasc.sentinel.models

case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None, labels: Option[DataPointLabels] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
case class SeqStatsAggr(read1: ReadStatsAggr, read2: Option[ReadStatsAggr] = None)

case class ReadStats(
  nBases: Long,
  nBasesA: Long,
  nBasesT: Long,
  nBasesG: Long,
  nBasesC: Long,
  nBasesN: Long,
  nReads: Long,
  nBasesByQual: Seq[Long],
  medianQualByPosition: Seq[Double])

// TODO: generate the aggregate stats programmatically (using macros?)
case class ReadStatsAggr(
  nBases: DataPointAggr,
  nBasesA: DataPointAggr,
  nBasesT: DataPointAggr,
  nBasesG: DataPointAggr,
  nBasesC: DataPointAggr,
  nBasesN: DataPointAggr,
  nReads: DataPointAggr)
