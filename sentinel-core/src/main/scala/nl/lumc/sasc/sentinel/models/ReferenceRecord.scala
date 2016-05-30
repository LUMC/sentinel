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

import com.novus.salat.annotations._
import com.novus.salat.annotations.Persist
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.utils.{ calcMd5, utcTimeNow }

/**
 * Representation of an alignment reference sequence.
 *
 * @param refId Database IDs.
 * @param contigs Record of all contigs / chromosomes in this reference sequence.
 * @param refName Reference sequence name.
 * @param creationTimeUtc UTC time when the reference record was created.
 */
case class ReferenceRecord(
  contigs: Seq[ReferenceSequenceRecord],
  refName: Option[String] = None,
  species: Option[String] = None,
  @Key("_id") refId: ObjectId = new ObjectId,
  creationTimeUtc: Date = utcTimeNow) {

  /** MD5 checksum of the concatenated string of all contig MD5 checksums, sorted alphabetically. */
  @Persist lazy val combinedMd5: String = calcMd5(contigs.map(_.md5).sorted)
}

/**
 * Representation of a reference sequence contig / chromosome.
 *
 * We try to follow the reference sequence dictionary definition as outlined in the
 * [[https://samtools.github.io/hts-specs/SAMv1.pdf SAM format specification]] as close as possible, except for the
 * md5sum (or M5 tag) of the sequence. In the SAM specification, this value is optional. However in our case, the value
 * is compulsory since we need them to compute a unique identifier for each reference.
 *
 * @param name Name of the contig (in a SAM file: SN tag).
 * @param length Length of the sequence (in a SAM file: LN tag).
 * @param assembly Genome assembly identifier (in a SAM file: AS tag).
 * @param md5 MD5 checksum of the sequence (in a SAM file: M5 tag).
 * @param species Species name (in a SAM file: SP tag).
 * @param uri URI (in a SAM file: UR tag).
 */
case class ReferenceSequenceRecord(
  name: String,
  length: Long,
  md5: String,
  assembly: Option[String] = None,
  species: Option[String] = None,
  uri: Option[String] = None)
