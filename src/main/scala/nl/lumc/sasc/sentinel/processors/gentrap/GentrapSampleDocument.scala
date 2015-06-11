package nl.lumc.sasc.sentinel.processors.gentrap

import java.util.Date

import com.novus.salat.annotations.Key
import org.bson.types.ObjectId

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.getTimeNow

case class GentrapSampleDocument(
  runId: ObjectId,
  referenceId: ObjectId,
  annotationIds: Seq[ObjectId],
  libs: Seq[GentrapLibDocument],
  alnStats: GentrapAlignmentStats,
  @Key("_id") id: ObjectId = new ObjectId,
  sampleName: Option[String] = None,
  runName: Option[String] = None,
  creationTimeUtc: Date = getTimeNow) extends BaseSampleDocument
