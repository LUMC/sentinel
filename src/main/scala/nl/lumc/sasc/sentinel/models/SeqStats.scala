package nl.lumc.sasc.sentinel.models

/**
 * Sequencing input statistics.
 *
 * @param read1 Statistics of the first read (if paired-end) or the only read (if single-end).
 * @param read2 Statistics of the second read. Only defined for paired-end inputs.
 * @param labels data point labels.
 */
case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None, labels: Option[DataPointLabels] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
/**
 * Aggregated sequencing input statistics.
 *
 * @param read1 Aggregated statistics of the first read (if paired-end) or the only read (if single-end).
 * @param read2 Aggregated statistics of the second read. Only defined for paired-end inputs.
 */
case class SeqStatsAggr(read1: ReadStatsAggr, read2: Option[ReadStatsAggr] = None)

/**
 * Statistics of a single read file.
 *
 * @param nBases Total number of bases from all reads.
 * @param nBasesA Total number of adenine bases from all reads.
 * @param nBasesT Total number of thymines from all reads.
 * @param nBasesG Total number of guanines from all reads.
 * @param nBasesC Total number of cytosines from all reads.
 * @param nBasesN Total number of unknown bases from all reads.
 * @param nReads Total number of reads.
 * @param nBasesByQual Values indicating how many bases have a given quality.
 * @param medianQualByPosition Values indicating the median base quality of each position.
 */
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
/** Aggregated statistics of a single read file. */
case class ReadStatsAggr(
  nBases: DataPointAggr,
  nBasesA: DataPointAggr,
  nBasesT: DataPointAggr,
  nBasesG: DataPointAggr,
  nBasesC: DataPointAggr,
  nBasesN: DataPointAggr,
  nReads: DataPointAggr)
