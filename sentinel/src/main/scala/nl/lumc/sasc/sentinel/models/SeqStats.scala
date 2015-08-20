/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.{ Persist, Salat }

import nl.lumc.sasc.sentinel.CaseClass
import nl.lumc.sasc.sentinel.utils.pctOf

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

/** Sequencing input statistics.*/
case class SeqStats(read1: ReadStats, read2: Option[ReadStats] = None, labels: Option[DataPointLabels] = None)
    extends SeqStatsLike[ReadStats] {

  /** Combined counts for both read1 and read2 (if present). */
  @Persist lazy val readAll: Option[ReadStats] = read2 match {
    case Some(r2) =>
      Option(ReadStats(
        nBases = read1.nBases + r2.nBases,
        nBasesA = read1.nBasesA + r2.nBasesA,
        nBasesT = read1.nBasesT + r2.nBasesT,
        nBasesG = read1.nBasesG + r2.nBasesG,
        nBasesC = read1.nBasesC + r2.nBasesC,
        nBasesN = read1.nBasesN + r2.nBasesN,
        nReads = read1.nReads, // nReads for each pair is equal
        nBasesByQual = Seq.empty[Long],
        medianQualByPosition = Seq.empty[Double]))
    case otherwise => Option(read1)
  }
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

/**
 * Statistics of a single read file.
 *
 * @param nBases Total number of bases across all reads.
 * @param nBasesA Total number of adenine bases across all reads.
 * @param nBasesT Total number of thymines across all reads.
 * @param nBasesG Total number of guanines across all reads.
 * @param nBasesC Total number of cytosines across all reads.
 * @param nBasesN Total number of unknown bases across all reads.
 * @param nReads Total number of reads.
 * @param nBasesByQual Sequence indicating how many bases have a given quality. The quality values correspond to the
 *                     array index (e.g. Seq(10) shows many bases have quality value 10 as quality values start from 0).
 * @param medianQualByPosition Sequence indicating the median quality value for a given read position. The position
 *                             correspond to the array index (e.g. Seq(20) shows the median quality value of read
 *                             position 21 since positions start from 1).
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
    medianQualByPosition: Seq[Double]) {

  /** Helper function to create a function that calculates percentages from number of aligned reads. */
  private def pctOfBases: Long => Double = pctOf(nBases)

  /** Percentage of adenine bases across all reads. */
  @Persist lazy val pctBasesA: Double = pctOfBases(nBasesA)

  /** Percentage of thymine bases across all reads. */
  @Persist lazy val pctBasesT: Double = pctOfBases(nBasesT)

  /** Percentage of guanine bases across all reads. */
  @Persist lazy val pctBasesG: Double = pctOfBases(nBasesG)

  /** Percentage of cytosine bases across all reads. */
  @Persist lazy val pctBasesC: Double = pctOfBases(nBasesC)

  /** Percentage of unknown bases across all reads. */
  @Persist lazy val pctBasesN: Double = pctOfBases(nBasesN)

  /** Percentage of guanine and cytosine bases across all reads. */
  @Persist lazy val pctBasesGC: Double = pctOfBases(nBasesG + nBasesC)
}

// TODO: generate the aggregate stats programmatically (using macros?)
/** Aggregated statistics of a single read file. */
case class ReadStatsAggr(
  nBases: DataPointAggr,
  nBasesA: DataPointAggr,
  nBasesT: DataPointAggr,
  nBasesG: DataPointAggr,
  nBasesC: DataPointAggr,
  nBasesN: DataPointAggr,
  nReads: DataPointAggr,
  pctBasesA: DataPointAggr,
  pctBasesT: DataPointAggr,
  pctBasesG: DataPointAggr,
  pctBasesC: DataPointAggr,
  pctBasesN: DataPointAggr,
  pctBasesGC: DataPointAggr)
