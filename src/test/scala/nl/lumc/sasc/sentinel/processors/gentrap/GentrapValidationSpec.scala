package nl.lumc.sasc.sentinel.processors.gentrap

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.JsonLoader
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

class GentrapValidationSpec extends Specification with JsonLoader with Mockito {

  val mongo = mock[MongodbAccessObject]

  "Support for the 'gentrap' pipeline" should {
    br

    val ipv04 = new GentrapV04InputProcessor(mongo)

    "exclude non-gentrap summary files" in {
      val summary = loadJson("/schema_examples/unsupported.json")
      ipv04.validator.validationMessages(summary) must not be empty
    }

    "exclude invalid summary files" in {
      val summary = loadJson("/schema_examples/invalid.json")
      ipv04.validator.validationMessages(summary) must not be empty
    }

    "include the v0.4 schema for" >> {

      "summaries with single samples and single libraries" in {
        val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_single_sample_single_lib.json")
        ipv04.validator.validationMessages(summary) must beEmpty
      }

      "summaries with single samples and multiple libraries" in {
        val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_single_sample_multi_lib.json")
        ipv04.validator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and single libraries" in {
        val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")
        ipv04.validator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and multiple libraries" in {
        val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_lib.json")
        ipv04.validator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and multiple libraries containing mixed library types" in {
        val summary = loadJson("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_lib_mixedlib.json")
        ipv04.validator.validationMessages(summary) must beEmpty
      }
    }
  }
}
