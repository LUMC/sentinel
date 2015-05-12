package nl.lumc.sasc.sentinel.models

case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None)
