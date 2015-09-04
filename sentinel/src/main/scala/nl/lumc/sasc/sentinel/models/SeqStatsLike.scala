package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

import nl.lumc.sasc.sentinel.CaseClass

/**
 * Trait for sequence statistics container.
 *
 * @tparam T Container for read-level statistics.
 */
@Salat trait SeqStatsLike[T <: CaseClass] { this: CaseClass =>

  /** Statistics of the first read. */
  val read1: T

  /** Statistics of the second read. */
  val read2: Option[T]

  /** Combined statistics of the first and second read. */
  val readAll: Option[_]
}

/**
 * Aggregated sequencing input statistics.
 *
 * @param read1 Aggregated statistics of the first read (if paired-end) or the only read (if single-end).
 * @param read2 Aggregated statistics of the second read. Only defined for paired-end inputs.
 * @param readAll Aggregated statistics of both reads. Only defined if there is at least a paired-end data point
 *                in aggregation.
 */
case class SeqStatsAggr[T <: CaseClass](read1: T, read2: Option[T] = None, readAll: Option[T] = None)
  extends SeqStatsLike[T]

