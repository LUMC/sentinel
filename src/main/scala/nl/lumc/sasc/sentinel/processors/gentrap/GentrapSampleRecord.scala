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

import java.util.Date

import com.novus.salat.annotations.Key
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

/**
 * Gentrap sample entry.
 *
 * @param uploaderId ID of the user that created the sample entry.
 * @param runId Database ID of the run to which this sample belongs to.
 * @param referenceId Database ID of the reference alignment record used by this sample.
 * @param annotationIds Database IDs of the annotation records used by this sample.
 * @param alnStats Sample-level alignment statistics.
 * @param sampleName Sample name.
 * @param runName Name of the run to which this sample belongs to.
 * @param creationTimeUtc UTC time when this sample entry was created.
 * @param dbId Internal database ID.
 */
case class GentrapSampleRecord(
  uploaderId: String,
  runId: ObjectId,
  referenceId: ObjectId,
  annotationIds: Seq[ObjectId],
  alnStats: GentrapAlignmentStats,
  sampleName: Option[String] = None,
  runName: Option[String] = None,
  creationTimeUtc: Date = getUtcTimeNow,
  @Key("_id") dbId: ObjectId = new ObjectId) extends BaseSampleRecord
