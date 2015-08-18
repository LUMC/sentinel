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

/** Representation of a sequencing accumulation level unit. */
@Salat abstract class BaseUnitRecord {

  /** Internal database ID for the document. */
  @Key("_id") def dbId: ObjectId

  /** Name of the uploader of the run summary which contains this unit. */
  def uploaderId: String

  /** Database sample ID. */
  def runId: ObjectId

  /** Name of the run that produced this unit. */
  def runName: Option[String]

  /** UTC time when the sample document was created. */
  def creationTimeUtc: Date
}

/** Representation of a sample within a run. */
@Salat abstract class BaseSampleRecord extends BaseUnitRecord {

  /** Sample name. */
  def sampleName: Option[String]
}

/** Representation of a read group metrics. */
@Salat abstract class BaseReadGroupRecord extends BaseUnitRecord {

  /** Name of the sample which this read group belongs to. */
  def sampleName: Option[String]

  /** Library name. */
  def readGroupName: Option[String]

  /** Short hand attribute that returns true if the read group was created from a paired-end sequence. */
  @Persist def isPaired: Boolean
}
