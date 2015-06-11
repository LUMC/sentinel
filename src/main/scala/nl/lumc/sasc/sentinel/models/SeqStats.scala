package nl.lumc.sasc.sentinel.models

case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
case class SeqAggregateStats(read1: ReadAggregateStats, read2: Option[ReadAggregateStats] = None)
