/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel.models

import org.specs2._

import nl.lumc.sasc.sentinel.{ Pipeline, SchemaVersion }

class GentrapSpec extends Specification with PipelineSpec { def is = s2"""

    The Gentrap v0.4 schema must
      return valid for multi sample, single library summary              $multiSampleSingleLib
      return valid for multi sample, multi library summary               $multiSampleMultiLib
"""

  val pipeline = Pipeline.Gentrap

  val schemaVersion = SchemaVersion.V04

  def multiSampleSingleLib = {
    val summary = loadResource("/v0.4/gentrap_multi_sample_single_lib.json")
    schema.isValid(summary) must beTrue
  }

  def multiSampleMultiLib = {
    val summary = loadResource("/v0.4/gentrap_multi_sample_multi_lib.json")
    schema.isValid(summary) must beTrue
  }

  // TODO: add test for single sample single lib
  // TODO: add test for multi sample single lib
  // TODO: add test for invalid summaries
}
