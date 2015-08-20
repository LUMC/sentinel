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
package nl.lumc.sasc.sentinel.exts.maple

import nl.lumc.sasc.sentinel.AccLevel
import nl.lumc.sasc.sentinel.processors.StatsProcessor
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class MapleStatsProcessor(mongo: MongodbAccessObject) extends StatsProcessor(mongo) {

  def pipelineName = "maple"

  /** Function for retrieving Maple read group data points. */
  def getMapleReadGroupStats = getStats[MapleReadGroupStats]("stats")(AccLevel.ReadGroup) _

  /* Function for aggregating over Maple read group data points. */
  def getMapleReadGroupAggrStats = getAggregateStats[MapleReadGroupStatsAggr]("stats")(AccLevel.ReadGroup) _

  /* Function for retrieving Maple sample data points. */
  def getMapleSampleStats = getStats[MapleSampleStats]("stats")(AccLevel.Sample) _

  /* Function for aggregating over Maple read group data points. */
  def getMapleSampleAggrStats = getAggregateStats[MapleSampleStatsAggr]("stats")(AccLevel.Sample) _
}