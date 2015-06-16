package nl.lumc.sasc.sentinel.utils

import com.github.fge.jsonschema.core.report.ProcessingReport

/** Exception that is thrown when a JSON validation fails. */
class RunValidationException(msg: String, val report: Option[ProcessingReport] = None)
  extends RuntimeException(msg)

