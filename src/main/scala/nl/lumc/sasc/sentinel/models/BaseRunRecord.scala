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

import java.util.Date

import com.novus.salat.annotations.{ Key, Persist, Salat }
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.CaseClass

/** Representation of an uploaded run summary file. */
@Salat abstract class BaseRunRecord { this: CaseClass =>

  /** Run name. */
  def runName: Option[String]

  /** Database run ID. */
  def runId: ObjectId

  /** Run uploader ID. */
  def uploaderId: String

  /** Name of the pipeline that produced the run. */
  def pipeline: String

  /** Sample IDs linked to this run. */
  def sampleIds: Seq[ObjectId]

  /** Library IDs linked to this run. */
  def readGroupIds: Seq[ObjectId]

  /** UTC time when the run record was created. */
  def creationTimeUtc: Date

  /** UTC time when the run record was deleted. */
  def deletionTimeUtc: Option[Date]

  /** Number of samples in the run summary file used for statistics. */
  @Persist val nSamples: Int = sampleIds.size

  /** Number of read groups in the run summary file used for statistics. */
  @Persist val nReadGroups: Int = readGroupIds.size
}

object BaseRunRecord {
  /** Attributes that is hidden when this object is serialized into JSON. */
  val hiddenAttributes = Set("sampleIds", "readGroupIds")
}

/**
 * Simple implementation of a run record with a single reference and multiple annotations.
 *
 * @param refId Reference record ID contained in the sample.
 * @param annotIds Annotation record IDs contained in the sample.
 */
case class RunRecord(
  @Key("_id") runId: ObjectId,
  uploaderId: String,
  pipeline: String,
  creationTimeUtc: Date,
  runName: Option[String] = None,
  deletionTimeUtc: Option[Date] = None,
  sampleIds: Seq[ObjectId] = Seq.empty,
  readGroupIds: Seq[ObjectId] = Seq.empty,
  refId: Option[ObjectId] = None,
  annotIds: Option[Seq[ObjectId]] = None) extends BaseRunRecord
