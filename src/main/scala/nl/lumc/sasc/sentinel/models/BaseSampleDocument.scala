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

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

/** Representation of a sample within a run. */
@Salat abstract class BaseSampleDocument {

  /** Name of the run summary file uploader. */
  def uploaderId: String

  /** Name of the run which this sample belongs to. */
  def runName: Option[String]

  /** Sample name. */
  def sampleName: Option[String]

  /** Database sample ID. */
  def runId: ObjectId

  /** Libraries belonging to this sample. */
  def libs: Seq[BaseLibDocument]

  /** UTC time when the sample document was created. */
  def creationTimeUtc: Date
}
