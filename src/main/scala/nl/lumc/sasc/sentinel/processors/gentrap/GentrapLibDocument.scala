package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models._

case class GentrapLibDocument(
  name: Option[String],
  rawRead1: GentrapReadDocument,
  processedRead1: Option[GentrapReadDocument] = None,
  rawRead2: Option[GentrapReadDocument] = None,
  processedRead2: Option[GentrapReadDocument] = None,
  alnStats: GentrapAlignmentStats) extends BaseLibDocument

