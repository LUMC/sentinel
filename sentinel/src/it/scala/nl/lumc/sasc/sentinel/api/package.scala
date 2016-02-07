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

import nl.lumc.sasc.sentinel.testing.PipelinePart

package object api {

  /** Convenience container for uploadable run summaries. */
  object SummaryExamples {

    trait PipelineUpload {

    }

    /** Plain summary file. */
    lazy val Plain = PipelinePart("/summary_examples/plain/plain.json", "plain")

    /** Plain summary file, compressed. */
    lazy val PlainCompressed = PipelinePart("/summary_examples/plain/plain.json.gz", "plain")

    /** Summary file that is JSON but invalid. */
    lazy val Invalid = PipelinePart("/summary_examples/invalid.json", "plain")

    /** Non-JSON file. */
    lazy val Not = PipelinePart("/summary_examples/not.json", "plain")

    object Maple {

      /** Single sample, single read group. */
      lazy val SSampleSRG = PipelinePart("/summary_examples/maple/maple_single_sample_single_rg.json", "maple")

      /** Single sample, multiple read groups. */
      lazy val SSampleMRG = PipelinePart("/summary_examples/maple/maple_single_sample_multi_rg.json", "maple")

      /** Multiple samples, single read group each. */
      lazy val MSampleSRG = PipelinePart("/summary_examples/maple/maple_multi_sample_single_rg.json", "maple")

      /** Multiple samples, multiple read groups. */
      lazy val MSampleMRG = PipelinePart("/summary_examples/maple/maple_multi_sample_multi_rg.json", "maple")

    }

    object Pref {

      /** Contains a reference. */
      lazy val Ref1 = PipelinePart("/summary_examples/pref/pref_01.json", "pref")

      /** Contains another reference. */
      lazy val Ref2 = PipelinePart("/summary_examples/pref/pref_02.json", "pref")

      /** Contains the same reference as Ref2. */
      lazy val Ref3 = PipelinePart("/summary_examples/pref/pref_03.json", "pref")
    }

    object Pann {

      /** Contains two annotations. */
      lazy val Ann1 = PipelinePart("/summary_examples/pann/pann_01.json", "pann")

      /** Contains one annotation already in Ann1. */
      lazy val Ann2 = PipelinePart("/summary_examples/pann/pann_02.json", "pann")
    }
  }
}
