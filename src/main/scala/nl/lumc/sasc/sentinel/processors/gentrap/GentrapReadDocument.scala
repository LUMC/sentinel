package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.models._

case class GentrapReadDocument(file: FileDocument, stats: ReadStats) extends BaseReadDocument

