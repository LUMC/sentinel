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

import com.novus.salat.annotations._
import org.bson.types.ObjectId

/**
 * Representation of an alignment reference sequence.
 *
 * @param refId Database IDs.
 * @param contigMd5s MD5 checksums of all contigs / chromosomes in this reference sequence.
 * @param combinedMd5 MD5 checksum of the concatenated string of all contig MD5 checksums, sorted alphabetically.
 * @param name Reference sequence name.
 * @param creationTimeUtc UTC time when the reference record was created.
 */
case class ReferenceRecord(
  @Key("_id") refId: ObjectId,
  contigMd5s: Seq[String],
  combinedMd5: String,
  name: Option[String] = None,
  creationTimeUtc: Option[Date] = None)
