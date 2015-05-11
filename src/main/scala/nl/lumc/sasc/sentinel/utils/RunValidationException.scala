package nl.lumc.sasc.sentinel.utils

import com.github.fge.jsonschema.core.report.ProcessingMessage

class RunValidationException(msg: String, val validationErrors: Seq[ProcessingMessage]) extends RuntimeException(msg)

