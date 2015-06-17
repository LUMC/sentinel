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

import nl.lumc.sasc.sentinel.models.{ DataPointAggr, DataPointLabels }

/**
 * Gentrap alignment statistics.
 *
 * @param nReads Number of reads.
 * @param nReadsAligned Number of reads aligned.
 * @param rateReadsMismatch Mismatch rate of aligned reads.
 * @param rateIndel How much indels are present.
 * @param nBasesAligned Number of bases aligned.
 * @param nBasesUtr Number of bases aligned in the UTR regions.
 * @param nBasesCoding Number of bases aligned in the coding regions.
 * @param nBasesIntron Number of bases aligned in the intronic regions.
 * @param nBasesIntergenic Number of bases aligned in the intergenic regions.
 * @param median5PrimeBias Median value of 5' coverage biases.
 * @param median3PrimeBias Median value of 3' coverage biases.
 * @param normalizedTranscriptCoverage Values representing normalized transcript coverage.
 * @param nBasesRibosomal Number of bases aligned to ribosomal gene regions.
 * @param pctChimeras Percentage of reads mapped as chimeras (only for paired-end libraries).
 * @param nSingletons Number of paired-end reads mapped as singletons.
 * @param maxInsertSize Maximum insert size (only for paired-end libraries).
 * @param medianInsertSize Median insert size (only for paired-end libraries).
 * @param stdevInsertSize Insert size standard deviation (only for paired-end libraries).
 * @param labels Data point labels.
 */
case class GentrapAlignmentStats(
  nReads: Long,
  nReadsAligned: Long,
  rateReadsMismatch: Double,
  rateIndel: Double,
  nBasesAligned: Long,
  nBasesUtr: Long,
  nBasesCoding: Long,
  nBasesIntron: Long,
  nBasesIntergenic: Long,
  median5PrimeBias: Double,
  median3PrimeBias: Double,
  normalizedTranscriptCoverage: Seq[Double],
  nBasesRibosomal: Option[Long] = None,
  pctChimeras: Option[Double] = None,
  nSingletons: Option[Long] = None,
  maxInsertSize: Option[Long] = None,
  medianInsertSize: Option[Long] = None,
  stdevInsertSize: Option[Double] = None,
  labels: Option[DataPointLabels] = None)

// TODO: generate the aggregate stats programmatically (using macros?)
/** Aggregated Gentrap alignment statistics. */
case class GentrapAlignmentStatsAggr(
  nReads: DataPointAggr,
  nReadsAligned: DataPointAggr,
  rateReadsMismatch: DataPointAggr,
  rateIndel: DataPointAggr,
  nBasesAligned: DataPointAggr,
  nBasesUtr: DataPointAggr,
  nBasesCoding: DataPointAggr,
  nBasesIntron: DataPointAggr,
  nBasesIntergenic: DataPointAggr,
  median5PrimeBias: DataPointAggr,
  median3PrimeBias: DataPointAggr,
  nBasesRibosomal: Option[DataPointAggr] = None,
  pctChimeras: Option[DataPointAggr] = None,
  nSingletons: Option[DataPointAggr] = None,
  maxInsertSize: Option[DataPointAggr] = None,
  medianInsertSize: Option[DataPointAggr] = None,
  stdevInsertSize: Option[DataPointAggr] = None)
