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

import nl.lumc.sasc.sentinel.AccLevel
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

  /** Retrieves alignment statistics per sample. */
  def getSampleAlignmentStats = getStatsByAcc[GentrapAlignmentStats]("alnStats")(AccLevel.Sample) _

  /** Retrieves alignment statistics per library. */
  def getLibAlignmentStats = getStatsByAcc[GentrapAlignmentStats]("alnStats")(AccLevel.Lib) _

  /** Retrieves aggregated alignment statistics. */
  def getAlignmentAggr = getAggrStatsByAcc[GentrapAlignmentStatsAggr]("alnStats") _

  /** Retrieves raw sequencing statistics data points. */
  def getSeqStatsRaw = getLibStats[SeqStats]("seqStatsRaw") _

  /** Retrieves processed sequencing statistics data points. */
  def getSeqStatsProcessed = getLibStats[SeqStats]("seqStatsProcessed") _

  /** Retrieves aggregated raw sequencing statistics. */
  def getSeqStatsAggrRaw = getSeqAggregateStats[ReadStatsAggr]("seqStatsRaw") _

  /** Retrieves aggregated processed sequencing statistics. */
  def getSeqStatsAggrProcessed = getSeqAggregateStats[ReadStatsAggr]("seqStatsProcessed") _
}
