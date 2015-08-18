/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.JsonLoader
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class GentrapValidationSpec extends Specification with JsonLoader with Mockito {

  val mongo = mock[MongodbAccessObject]

  "Support for the 'gentrap' pipeline" should {
    br

    val ipv04 = new GentrapV04RunsProcessor(mongo)

    "exclude non-gentrap summary files" in {
      val summary = loadJson("/summary_examples/plain.json")
      ipv04.jsonValidator.validationMessages(summary) must not be empty
    }

    "exclude invalid summary files" in {
      val summary = loadJson("/summary_examples/invalid.json")
      ipv04.jsonValidator.validationMessages(summary) must not be empty
    }

    "include the v0.4 schema for" >> {

      "summaries with single samples and single read groups" in {
        val summary = loadJson("/summary_examples/biopet/v0.4/gentrap_single_sample_single_rg.json")
        ipv04.jsonValidator.validationMessages(summary) must beEmpty
      }

      "summaries with single samples and multiple read groups" in {
        val summary = loadJson("/summary_examples/biopet/v0.4/gentrap_single_sample_multi_rg.json")
        ipv04.jsonValidator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and single read groups" in {
        val summary = loadJson("/summary_examples/biopet/v0.4/gentrap_multi_sample_single_rg.json")
        ipv04.jsonValidator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and multiple read groups" in {
        val summary = loadJson("/summary_examples/biopet/v0.4/gentrap_multi_sample_multi_rg.json")
        ipv04.jsonValidator.validationMessages(summary) must beEmpty
      }

      "summaries with multiple samples and multiple read groups containing mixed library types" in {
        val summary = loadJson("/summary_examples/biopet/v0.4/gentrap_multi_sample_multi_rg_mixedlib.json")
        ipv04.jsonValidator.validationMessages(summary) must beEmpty
      }
    }
  }
}
