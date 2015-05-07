/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel.models

import org.specs2._

import nl.lumc.sasc.sentinel.processors.gentrap.GentrapProcessorV04

class GentrapV04ValidationSpec extends Specification with SchemaValidationSpec { def is = s2"""

    The Gentrap v0.4 schema must
      be valid for multi sample, single library summary              $multiSampleSingleLibV04
      be valid for multi sample, multi library summary               $multiSampleMultiLibV04
"""

  object GentrapV04Module extends GentrapProcessorV04 with MockDatabaseProvider

  def multiSampleSingleLibV04 = {
    val summary = loadJson("/v0.4/gentrap_multi_sample_single_lib.json")
    GentrapV04Module.validate(summary).toList must beEmpty
  }

  def multiSampleMultiLibV04 = {
    val summary = loadJson("/v0.4/gentrap_multi_sample_multi_lib.json")
    GentrapV04Module.validate(summary).toList must beEmpty
  }

  // TODO: add test for single sample single lib
  // TODO: add test for multi sample single lib
  // TODO: add test for invalid summaries
}
