package nl.lumc.sasc.sentinel.processors.gentrap

import org.bson.types.ObjectId

import com.novus.salat.annotations.Key

import nl.lumc.sasc.sentinel.models._

case class GentrapSampleDocument(
  runId: ObjectId,
  referenceId: ObjectId,
  annotationIds: Seq[ObjectId],
  libs: Seq[GentrapLibDocument],
  @Key("_id") id: ObjectId = new ObjectId,
  name: Option[String] = None,
  alnStats: Option[GentrapAlignmentStats] = None) extends BaseSampleDocument
