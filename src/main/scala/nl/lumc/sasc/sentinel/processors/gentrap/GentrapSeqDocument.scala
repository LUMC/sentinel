package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models.BaseSeqDocument

case class GentrapSeqDocument(
  read1: GentrapReadDocument,
  read2: Option[GentrapReadDocument]) extends BaseSeqDocument
