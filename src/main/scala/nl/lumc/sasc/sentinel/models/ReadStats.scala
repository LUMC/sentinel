package nl.lumc.sasc.sentinel.models

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
case class ReadAggregateStats(
  nBases: AggrStat,
  nBasesA: AggrStat,
  nBasesT: AggrStat,
  nBasesG: AggrStat,
  nBasesC: AggrStat,
  nBasesN: AggrStat,
  nReads: AggrStat)
