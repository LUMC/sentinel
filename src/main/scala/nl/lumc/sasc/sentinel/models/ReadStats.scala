package nl.lumc.sasc.sentinel.models

case class ReadStats(
  nBases: Long,
  nBasesA: Long,
  nBasesT: Long,
  nBasesG: Long,
  nBasesC: Long,
  nBasesN: Long,
  nBasesByQual: Seq[Long],
  medianQualByPosition: Seq[Double],
  nReads: Long)

