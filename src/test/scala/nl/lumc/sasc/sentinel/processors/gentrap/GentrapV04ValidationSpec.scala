/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel.processors.gentrap

import org.specs2._
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.JsonLoaderSpec
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

class GentrapV04ValidationSpec extends Specification with JsonLoaderSpec with Mockito { def is = s2"""

  The Gentrap v0.4 schema must
    be valid for multi sample, single library summary              $multiSampleSingleLibV04
    be valid for multi sample, multi library summary               $multiSampleMultiLibV04
"""

  val mongo = mock[MongodbAccessObject]

  val gentrapV04InputProcessor = new GentrapV04InputProcessor(mongo)

  def multiSampleSingleLibV04 = {
    val summary = loadJson("/v0.4/gentrap_multi_sample_single_lib.json")
    gentrapV04InputProcessor.validate(summary).toList must beEmpty
  }

  def multiSampleMultiLibV04 = {
    val summary = loadJson("/v0.4/gentrap_multi_sample_multi_lib.json")
    gentrapV04InputProcessor.validate(summary).toList must beEmpty
  }

  // TODO: add test for single sample single lib
  // TODO: add test for multi sample single lib
  // TODO: add test for invalid summaries
}
