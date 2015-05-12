package nl.lumc.sasc.sentinel.utils

class DuplicateRunException(val existingRunId: Option[String] = None) extends RuntimeException
