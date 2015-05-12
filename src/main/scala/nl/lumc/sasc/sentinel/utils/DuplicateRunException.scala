package nl.lumc.sasc.sentinel.utils

import com.mongodb.DuplicateKeyException

class DuplicateRunException(val existingRunId: Option[String] = None) extends DuplicateKeyException

