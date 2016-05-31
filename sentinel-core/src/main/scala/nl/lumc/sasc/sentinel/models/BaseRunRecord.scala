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
package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.{Ignore, Key, Persist, Salat}
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.utils.utcTimeNow

/** Representation of a stored unit. */
@Salat abstract class BaseUnitRecord { this: CaseClass =>

  /** Run ID. */
  def runId: ObjectId

  /** Labels. */
  def labels: UnitLabels

  /** Run uploader ID. */
  def uploaderId: String

  /** UTC time when the record was created. */
  def creationTimeUtc: Date
}

/** Representation of an uploaded run summary file. */
@Salat abstract class BaseRunRecord extends BaseUnitRecord { this: CaseClass =>

  /** Database run ID. */
  @Key("_id") def runId: ObjectId

  /** Name of the pipeline that produced the run. */
  def pipeline: String

  /** Labels. */
  def labels: RunLabelsLike

  /** Sample IDs linked to this run. */
  def sampleIds: Seq[ObjectId]

  /** Library IDs linked to this run. */
  def readGroupIds: Seq[ObjectId]

  /** UTC time when the run record was deleted. */
  def deletionTimeUtc: Option[Date]

  /** Number of samples in the run summary file used for statistics. */
  @Persist val nSamples: Int = sampleIds.size

  /** Number of read groups in the run summary file used for statistics. */
  @Persist val nReadGroups: Int = readGroupIds.size

  /**
   * IDs, tags, and labels of each sample.
   *
   * This field is meant to be filled by this run's processor directly from the database.
   */
  @Ignore val sampleLabels: Map[String, SampleLabelsLike]

  /**
   * IDs, tags, and labels of each read group.
   *
   * This field is meant to be filled by this run's processor directly from the database.
   */
  @Ignore val readGroupLabels: Map[String, ReadGroupLabelsLike]
}

object BaseRunRecord {
  /** Attributes that is hidden when this object is serialized into JSON. */
  val hiddenAttributes = Set("sampleIds", "readGroupIds")
}

/** Representation of a sample within a run. */
@Salat abstract class BaseSampleRecord extends BaseUnitRecord { this: CaseClass =>

  /** Internal database ID for the document. */
  @Key("_id") def dbId: ObjectId

  /** Labels. */
  def labels: SampleLabelsLike

  @Persist val creationTimeUtc: Date = utcTimeNow
}

/** Representation of a read group metrics. */
@Salat abstract class BaseReadGroupRecord extends BaseUnitRecord { this: CaseClass =>

  /** Internal database ID for the document. */
  @Key("_id") def dbId: ObjectId

  /** Database ID of the sample record linked to this read group. */
  def sampleId: ObjectId

  /** Labels. */
  def labels: ReadGroupLabelsLike

  /** Short hand attribute that returns true if the read group was created from a paired-end sequence. */
  def isPaired: Boolean

  @Persist val creationTimeUtc: Date = utcTimeNow
}
