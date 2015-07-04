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

import com.novus.salat.annotations.{ Key, Persist }
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

/**
 * Gentrap library entry.
 *
 * @param alnStats Alignment statistics of the library.
 * @param seqStatsRaw Sequencing statistics of the raw input sequencing files.
 * @param seqStatsProcessed Sequencing statistics of the QC-ed input sequencing files.
 * @param seqFilesRaw Raw input sequencing file entries.
 * @param seqFilesProcessed QC-ed sequencing file entries.
 * @param referenceId Database ID of the reference alignment record used by this sample.
 * @param annotationIds Database IDs of the annotation records used by this sample.
 * @param isPaired Whether the library is paired-end or not.
 * @param libName Library name.
 * @param sampleName Name of the sample to which the library belongs to.
 * @param runName Name of the run to which the library belongs to.
 * @param uploaderId ID of the user that created the sample entry.
 * @param creationTimeUtc UTC time when this sample entry was created.
 * @param id Internal database ID.
 */
case class GentrapLibDocument(
  alnStats: GentrapAlignmentStats,
  seqStatsRaw: SeqStats,
  seqStatsProcessed: Option[SeqStats],
  seqFilesRaw: SeqFiles,
  seqFilesProcessed: Option[SeqFiles],
  referenceId: ObjectId,
  annotationIds: Seq[ObjectId],
  isPaired: Boolean,
  uploaderId: String,
  runId: ObjectId,
  libName: Option[String] = None,
  sampleName: Option[String] = None,
  runName: Option[String] = None,
  creationTimeUtc: Date = getUtcTimeNow,
  @Key("_id") id: ObjectId = new ObjectId) extends BaseLibDocument
