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
