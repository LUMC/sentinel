package nl.lumc.sasc.sentinel.models

case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
case class SeqStatsAggr(read1: ReadStatsAggr, read2: Option[ReadStatsAggr] = None)
