package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models._

case class GentrapSampleDocument(
  name: Option[String] = None,
  runId: String,
  referenceId: String,
  annotationIds: Seq[String],
  libs: Seq[GentrapLibDocument],
  alnStats: Option[GentrapAlignmentStats] = None) extends BaseSampleDocument
