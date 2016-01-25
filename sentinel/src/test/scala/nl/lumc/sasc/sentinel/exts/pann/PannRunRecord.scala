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
package nl.lumc.sasc.sentinel.exts.pann

import java.util.Date

import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.utcTimeNow

/** Container for a Pann run. */
case class PannRunRecord(
    runId: ObjectId,
    uploaderId: String,
    pipeline: String,
    sampleIds: Seq[ObjectId],
    annotIds: Seq[ObjectId],
    runName: Option[String] = None,
    deletionTimeUtc: Option[Date] = None,
    creationTimeUtc: Date = utcTimeNow) extends BaseRunRecord {

  lazy val readGroupIds = Seq.empty[ObjectId]

}

/** Container for a single Pann sample. */
case class PannSampleRecord(
  stats: PannSampleStats,
  uploaderId: String,
  runId: ObjectId,
  annotIds: Seq[ObjectId],
  sampleName: Option[String] = None,
  runName: Option[String] = None) extends BaseSampleRecord

/** Container for a single Pann sample statistics. */
case class PannSampleStats(num: Long)
