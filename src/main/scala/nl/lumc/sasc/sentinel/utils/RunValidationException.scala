package nl.lumc.sasc.sentinel.utils

import com.github.fge.jsonschema.core.report.ProcessingReport

class RunValidationException(msg: String, val report: Option[ProcessingReport] = None)
  extends RuntimeException(msg)

