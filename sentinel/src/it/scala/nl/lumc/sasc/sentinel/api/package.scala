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
package nl.lumc.sasc.sentinel

import nl.lumc.sasc.sentinel.testing.SentinelServletSpec._

package object api {

  /** Convenience container for uploadable run summaries. */
  object SummaryExamples {

    /** Plain summary file. */
    lazy val Plain = makeUploadable("/summary_examples/plain/plain.json")

    /** Plain summary file, compressed. */
    lazy val PlainCompressed = makeUploadable("/summary_examples/plain/plain.json.gz")

    /** Summary file that is JSON but invalid. */
    lazy val Invalid = makeUploadable("/summary_examples/invalid.json")

    /** Non-JSON file. */
    lazy val Not = makeUploadable("/summary_examples/not.json")

    object Maple {

      /** Single sample, single read group. */
      lazy val SSampleSRG = makeUploadable("/summary_examples/maple/maple_single_sample_single_rg.json")

      /** Single sample, multiple read groups. */
      lazy val SSampleMRG = makeUploadable("/summary_examples/maple/maple_single_sample_multi_rg.json")

      /** Multiple samples, single read group each. */
      lazy val MSampleSRG = makeUploadable("/summary_examples/maple/maple_multi_sample_single_rg.json")

      /** Multiple samples, multiple read groups. */
      lazy val MSampleMRG = makeUploadable("/summary_examples/maple/maple_multi_sample_multi_rg.json")

    }

    object Pref {

      /** Contains a reference. */
      lazy val Ref1 = makeUploadable("/summary_examples/pref/pref_01.json")

      /** Contains another reference. */
      lazy val Ref2 = makeUploadable("/summary_examples/pref/pref_02.json")

      /** Contains the same reference as Ref2. */
      lazy val Ref3 = makeUploadable("/summary_examples/pref/pref_03.json")
    }

    object Pann {

      /** Contains two annotations. */
      lazy val Ann1 = makeUploadable("/summary_examples/pann/pann_01.json")

      /** Contains one annotation already in Ann1. */
      lazy val Ann2 = makeUploadable("/summary_examples/pann/pann_02.json")
    }
  }
}
