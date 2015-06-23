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
package nl.lumc.sasc.sentinel.processors.gentrap

import com.novus.salat.annotations.Persist

import nl.lumc.sasc.sentinel.models.{ DataPointAggr, DataPointLabels }
import nl.lumc.sasc.sentinel.utils.pctOf

/**
 * Gentrap alignment statistics.
 *
 * @param labels Data point labels.
 * @param maxInsertSize Maximum insert size (only for paired-end libraries).
 * @param median3PrimeBias Median value of 3' coverage biases.
 * @param median5PrimeBias Median value of 5' coverage biases.
 * @param medianInsertSize Median insert size (only for paired-end libraries).
 * @param nBasesAligned Number of bases aligned.
 * @param nBasesCoding Number of bases aligned in the coding regions.
 * @param nBasesIntergenic Number of bases aligned in the intergenic regions.
 * @param nBasesIntron Number of bases aligned in the intronic regions.
 * @param nBasesRibosomal Number of bases aligned to ribosomal gene regions.
 * @param nBasesUtr Number of bases aligned in the UTR regions.
 * @param normalizedTranscriptCoverage Values representing normalized transcript coverage.
 * @param nReadsAligned Number of reads aligned.
 * @param nReadsSingleton Number of paired-end reads aligned as singletons.
 * @param nReadsTotal Number of reads.
 * @param nReadsProperPair Number of paired-end reads aligned as proper pairs.
 * @param pctChimeras Percentage of reads aligned as chimeras (only for paired-end libraries).
 * @param rateIndel How much indels are present.
 * @param rateReadsMismatch Mismatch rate of aligned reads.
 * @param stdevInsertSize Insert size standard deviation (only for paired-end libraries).
 */
case class GentrapAlignmentStats(
    labels: Option[DataPointLabels] = None,
    maxInsertSize: Option[Long] = None,
    medianInsertSize: Option[Long] = None,
    median3PrimeBias: Double,
    median5PrimeBias: Double,
    nReadsAligned: Long,
    nBasesAligned: Long,
    nBasesCoding: Long,
    nBasesIntergenic: Long,
    nBasesIntron: Long,
    nBasesRibosomal: Option[Long] = None,
    nBasesUtr: Long,
    normalizedTranscriptCoverage: Seq[Double],
    nReadsProperPair: Option[Long] = None,
    nReadsSingleton: Option[Long] = None,
    nReadsTotal: Long,
    pctChimeras: Option[Double] = None,
    rateIndel: Double,
    rateReadsMismatch: Double,
    stdevInsertSize: Option[Double] = None) {

  /** Helper function to create a function that calculates percentages from number of aligned reads. */
  private def pctOfAlignedReads: Long => Double = pctOf(nReadsAligned)

  /** Helper function to create a function that calculates percentages from number of aligned bases. */
  private def pctOfAlignedBases: Long => Double = pctOf(nBasesAligned)

  /** Number of bases aligned in the UTR and coding regions. */
  @Persist lazy val nBasesMrna: Long = nBasesUtr + nBasesUtr

  /** Percentage of bases aligned to the coding regions (from total number of aligned bases). */
  @Persist lazy val pctBasesCoding: Double = pctOfAlignedBases(nBasesCoding)

  /** Percentage of bases aligned to the intergenic regions (from total number of aligned bases). */
  @Persist lazy val pctBasesIntergenic: Double = pctOfAlignedBases(nBasesIntergenic)

  /** Percentage of bases aligned to the intron regions (from total number of aligned bases). */
  @Persist lazy val pctBasesIntron: Double = pctOfAlignedBases(nBasesIntron)

  /** Percentage of bases aligned to UTR and coding regions (from total number of aligned bases). */
  @Persist lazy val pctBasesMrna: Double = pctOfAlignedBases(nBasesMrna)

  /** Percentage of bases aligned to the UTR regions (from total number of aligned bases). */
  @Persist lazy val pctBasesUtr: Double = pctOfAlignedBases(nBasesUtr)

  /** Percentage of bases aligned to ribosomal gene regions (from total number of aligned bases). */
  @Persist lazy val pctBasesRibosomal: Option[Double] = nBasesRibosomal.map(pctOfAlignedBases)

  /** Percentage of reads aligned (from total number of reads). */
  @Persist lazy val pctReadsAlignedTotal: Double = pctOf(nReadsTotal)(nReadsAligned)

  /** Percentage of reads aligned as proper pairs (from total number of aligned reads). */
  @Persist lazy val pctReadsProperPair: Option[Double] = nReadsProperPair.map(pctOfAlignedReads)

  /** Percentage of reads aligned as singletons (from total number of aligned reads). */
  @Persist lazy val pctReadsSingleton: Option[Double] = nReadsSingleton.map(pctOfAlignedReads)
}

// TODO: generate the aggregate stats programmatically (using macros?)
/** Aggregated Gentrap alignment statistics. */
case class GentrapAlignmentStatsAggr(
  maxInsertSize: Option[DataPointAggr] = None,
  median3PrimeBias: DataPointAggr,
  median5PrimeBias: DataPointAggr,
  medianInsertSize: Option[DataPointAggr] = None,
  nBasesAligned: DataPointAggr,
  nBasesCoding: DataPointAggr,
  nBasesIntergenic: DataPointAggr,
  nBasesIntron: DataPointAggr,
  nBasesMrna: DataPointAggr,
  nBasesRibosomal: Option[DataPointAggr] = None,
  nBasesUtr: DataPointAggr,
  nReadsAligned: DataPointAggr,
  nReadsSingleton: Option[DataPointAggr] = None,
  nReadsProperPair: Option[DataPointAggr] = None,
  nReadsTotal: DataPointAggr,
  pctBasesCoding: DataPointAggr,
  pctBasesMrna: DataPointAggr,
  pctBasesIntergenic: DataPointAggr,
  pctBasesIntron: DataPointAggr,
  pctBasesRibosomal: Option[DataPointAggr] = None,
  pctBasesUtr: DataPointAggr,
  pctChimeras: Option[DataPointAggr] = None,
  pctReadsAlignedTotal: DataPointAggr,
  pctReadsProperPair: Option[DataPointAggr] = None,
  pctReadsSingleton: Option[DataPointAggr] = None,
  rateIndel: DataPointAggr,
  rateReadsMismatch: DataPointAggr,
  stdevInsertSize: Option[DataPointAggr] = None)
