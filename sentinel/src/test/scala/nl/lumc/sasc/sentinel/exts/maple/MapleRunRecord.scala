/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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

import java.util.Date

import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.utcTimeNow

/** Container for a Maple run. */
case class MapleRunRecord(
  runId: ObjectId,
  uploaderId: String,
  pipeline: String,
  sampleIds: Seq[ObjectId],
  readGroupIds: Seq[ObjectId],
  labels: RunLabels = RunLabels(),
  deletionTimeUtc: Option[Date] = None,
  creationTimeUtc: Date = utcTimeNow) extends BaseRunRecord

/** Container for a single Maple sample. */
case class MapleSampleRecord(
  stats: MapleSampleStats,
  uploaderId: String,
  dbId: ObjectId,
  runId: ObjectId,
  labels: SampleLabels = SampleLabels()) extends BaseSampleRecord

/** Container for a single Maple sample statistics. */
case class MapleSampleStats(
  nSnps: Long,
  labels: Option[DataPointLabels] = None) extends LabeledStats

/** Container for aggregated Maple sample statistics. */
case class MapleSampleStatsAggr(nSnps: DataPointAggr)

/** Container for a single Maple read group. */
case class MapleReadGroupRecord(
  stats: MapleReadGroupStats,
  uploaderId: String,
  dbId: ObjectId,
  sampleId: ObjectId,
  runId: ObjectId,
  isPaired: Boolean = true,
  labels: ReadGroupLabels = ReadGroupLabels()) extends BaseReadGroupRecord

/** Container for a single Maple read group statistics. */
case class MapleReadGroupStats(
  nReadsInput: Long,
  nReadsAligned: Long,
  labels: Option[DataPointLabels] = None) extends LabeledStats

/** Container for aggregated Maple read group statistics. */
case class MapleReadGroupStatsAggr(
  nReadsInput: DataPointAggr,
  nReadsAligned: DataPointAggr)
