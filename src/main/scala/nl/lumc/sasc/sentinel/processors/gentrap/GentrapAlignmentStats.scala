package nl.lumc.sasc.sentinel.processors.gentrap

case class GentrapAlignmentStats(
  nReads: Long,
  nReadsAligned: Long,
  rateReadsMismatch: Double,
  rateIndel: Double,
  pctChimeras: Option[Double] = None,
  nSingletons: Option[Long] = None,
  maxInsertSize: Option[Long] = None,
  medianInsertSize: Option[Long] = None,
  stdevInsertSize: Option[Double] = None,
  nBasesAligned: Long,
  nBasesUtr: Long,
  nBasesCoding: Long,
  nBasesIntron: Long,
  nBasesIntergenic: Long,
  nBasesRibosomal: Option[Long],
  median5PrimeBias: Double,
  median3PrimeBias: Double,
  normalizedTranscriptCoverage: Seq[Double])

