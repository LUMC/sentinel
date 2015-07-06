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

import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.StatsProcessor

/**
 * Output processor for Gentrap endpoints.
 *
 * @param mongo MongoDB database access object.
 */
class GentrapStatsProcessor(mongo: MongodbAccessObject) extends StatsProcessor(mongo) {

  def pipelineName = "gentrap"

  /** Attribute names of the values to aggregate for alignment metrics. */
  val alnAttrs = Seq(
    "maxInsertSize",
    "median3PrimeBias",
    "median5PrimeBias",
    "median5PrimeTo3PrimeBias",
    "medianInsertSize",
    "nBasesAligned",
    "nBasesCoding",
    "nBasesMrna",
    "nBasesIntergenic",
    "nBasesIntron",
    "nBasesRibosomal",
    "nBasesUtr",
    "nReadsAligned",
    "nReadsSingleton",
    "nReadsProperPair",
    "nReadsTotal",
    "pctBasesCoding",
    "pctBasesIntergenic",
    "pctBasesIntron",
    "pctBasesMrna",
    "pctBasesRibosomal",
    "pctBasesUtr",
    "pctChimeras",
    "pctReadsAlignedTotal",
    "pctReadsProperPair",
    "pctReadsSingleton",
    "rateIndel",
    "rateReadsMismatch",
    "stdevInsertSize")

  // TODO: generate the attribute names programmatically (using macros?)
  /** Attribute names of the values to aggregate for read metrics. */
  val seqAttrs = Seq(
    "nBases",
    "nBasesA",
    "nBasesT",
    "nBasesG",
    "nBasesC",
    "nBasesN",
    "nReads",
    "pctBasesA",
    "pctBasesT",
    "pctBasesG",
    "pctBasesC",
    "pctBasesN",
    "pctBasesGC")

  /** Retrieves alignment statistics data points. */
  def getAlignmentStats = getStatsByAcc[GentrapAlignmentStats]("alnStats") _

  /** Retrieves aggregated alignment statistics. */
  def getAlignmentAggr = getAggrStatsByAcc[GentrapAlignmentStatsAggr]("alnStats", alnAttrs) _

  /** Retrieves raw sequencing statistics data points. */
  def getSeqStatsRaw = getLibStats[SeqStats]("seqStatsRaw") _

  /** Retrieves processed sequencing statistics data points. */
  def getSeqStatsProcessed = getLibStats[SeqStats]("seqStatsProcessed") _

  /** Retrieves aggregated raw sequencing statistics. */
  def getSeqStatsAggrRaw = getSeqAggregateStats[ReadStatsAggr]("seqStatsRaw", seqAttrs) _

  /** Retrieves aggregated processed sequencing statistics. */
  def getSeqStatsAggrProcessed = getSeqAggregateStats[ReadStatsAggr]("seqStatsProcessed", seqAttrs) _
}
