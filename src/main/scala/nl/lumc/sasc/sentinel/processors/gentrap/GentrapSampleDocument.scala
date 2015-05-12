package nl.lumc.sasc.sentinel.processors.gentrap

import org.bson.types.ObjectId

import com.novus.salat.annotations.Key

import nl.lumc.sasc.sentinel.models._

case class GentrapSampleDocument(
  @Key("_id") runId: ObjectId,
  referenceId: String,
  annotationIds: Seq[String],
  libs: Seq[GentrapLibDocument],
  name: Option[String] = None,
  alnStats: Option[GentrapAlignmentStats] = None) extends BaseSampleDocument
