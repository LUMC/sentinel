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
 * @param libs Libraries that compose this sample.
 * @param alnStats Sample-level alignment statistics.
 * @param sampleName Sample name.
 * @param runName Name of the run to which this sample belongs to.
 * @param creationTimeUtc UTC time when this sample entry was created.
 */
case class GentrapSampleDocument(
  uploaderId: String,
  runId: ObjectId,
  referenceId: ObjectId,
  annotationIds: Seq[ObjectId],
  libs: Seq[GentrapLibDocument],
  alnStats: GentrapAlignmentStats,
  @Key("_id") id: ObjectId = new ObjectId,
  sampleName: Option[String] = None,
  runName: Option[String] = None,
  creationTimeUtc: Date = getUtcTimeNow) extends BaseSampleDocument
