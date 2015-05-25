package nl.lumc.sasc.sentinel.processors.gentrap

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.JsonLoader
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

class GentrapV04ValidationSpec extends Specification with JsonLoader with Mockito {

  val mongo = mock[MongodbAccessObject]
  val ipv04 = new GentrapV04InputProcessor(mongo)

  "Gentrap v0.4 schema" should {

    "be valid for summaries with multiple samples and single libraries" in {
      val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")
      ipv04.validator.validationMessages(summary) must beEmpty
    }

    "be valid for summaries with multiple samples and multiple libraries" in {
      val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_lib.json")
      ipv04.validator.validationMessages(summary) must beEmpty
    }
  }

  // TODO: add test for single sample single lib
  // TODO: add test for multi sample single lib
  // TODO: add test for invalid summaries
}
