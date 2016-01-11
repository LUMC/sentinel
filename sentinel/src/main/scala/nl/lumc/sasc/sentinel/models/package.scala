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

import scala.concurrent.Future

import scalaz._

package object models {

  /** Type alias for case classes. */
  type CaseClass = AnyRef with Product

  /** Type alias for operations that returns a user-visible payload when failing. */
  type Perhaps[+T] = ApiPayload \/ T

  /** Type alias for stacking `Future` and scalaz's `\/`. */
  type AsyncPerhaps[+T] = EitherT[Future, ApiPayload, T]

  /** Supported statistics accumulation level */
  object AccLevel extends Enumeration {
    type AccLevel = Value
    val ReadGroup = Value("readGroup")
    val Sample = Value("sample")
  }

  /** Supported library types */
  object LibType extends Enumeration {
    type LibType = Value
    val Single = Value("single")
    val Paired = Value("paired")
  }

  /** Supported QC step for sequences */
  object SeqQcPhase extends Enumeration {
    type SeqQcPhase = Value
    val Raw = Value("raw")
    val Processed = Value("processed")
  }

  /** Supported aggregation parameters */
  object AggrStat extends Enumeration {
    type AggrStat = Value
    val Sum = Value("sum")
    val Min = Value("min")
    val Max = Value("max")
    val Mean = Value("mean")
    val Median = Value("median")
    val Variance = Value("variance")
    val Stdev = Value("stdev")
  }
}
