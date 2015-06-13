package nl.lumc.sasc.sentinel.models

case class SeqFiles(read1: FileDocument, read2: Option[FileDocument] = None)
