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
package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.api.SentinelServletSpec.makeUploadable

/** Convenience container for uploadable run summaries. */
object LumcSummaryExamples {

  object Gentrap {

    object V04 {
      // 1 sample, 1 rg
      lazy val SSampleSRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_rg.json")
      // 1 sample, 2 rgs
      lazy val SSampleMRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_multi_rg.json")
      // 2 samples (1, 1) rgs
      lazy val MSampleSRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_rg.json")
      // 3 samples, (3, 2, 1) rgs
      lazy val MSampleMRG = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_rg.json")
      // 3 samples (3: single, 1: single, 2: paired) rgs
      lazy val MSampleMRGMixedLib =
        makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_multi_rg_mixedlib.json")
    }
  }
}
