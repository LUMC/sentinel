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
package nl.lumc.sasc.sentinel.biopet.gentrap

import java.util.Date

import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models.BaseRunRecord

/**
 * Simple implementation of a run record with a single reference and multiple annotations.
 *
 * @param refId Reference record ID contained in the sample.
 * @param annotIds Annotation record IDs contained in the sample.
 */
case class GentrapRunRecord(
  runId: ObjectId,
  uploaderId: String,
  pipeline: String,
  creationTimeUtc: Date,
  runName: Option[String] = None,
  deletionTimeUtc: Option[Date] = None,
  sampleIds: Seq[ObjectId] = Seq.empty,
  readGroupIds: Seq[ObjectId] = Seq.empty,
  refId: Option[ObjectId] = None,
  annotIds: Option[Seq[ObjectId]] = None) extends BaseRunRecord
