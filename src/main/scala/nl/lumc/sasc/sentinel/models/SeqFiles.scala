package nl.lumc.sasc.sentinel.models

/**
 * Sequencing input files, which can be single-end or paired-end.
 *
 * @param read1 The first read (if paired-end) or the only read (if single end).
 * @param read2 The second read. Only defined for paired-end inputs.
 */
case class SeqFiles(read1: FileDocument, read2: Option[FileDocument] = None)
