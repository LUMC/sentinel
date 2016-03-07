/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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
package nl.lumc.sasc.sentinel.processors

import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.SinglePathPatchJsonExtractor

/** Object for patching runs. */
class RunsPatcher extends SinglePathPatchJsonExtractor {

  override def patchValidationFuncs = super.patchValidationFuncs :+ mustHaveValidPathAndValue

  override protected val validOps: Set[String] = Set("replace")

  /** Valid paths for the patch operation. */
  protected val validPatchPaths = Set("/runName")

  /** Validation function that ensures the patch operation have the valid paths and values. */
  protected final val mustHaveValidPathAndValue: ValidationFunc =
    (ops: Seq[SinglePathPatch]) => {
      val vms = ops.flatMap { p =>
        (p.path, p.value) match {
          case ("/runName", v: String)               => List.empty
          case (x, y) if validPatchPaths.contains(x) => List(s"Invalid value for path '$x': '$y'.")
          case (ip, _)                               => List(s"Invalid path: '$ip'.")
        }
      }
      vms.toList.toNel match {
        case Some(nel) => nel.failure
        case None      => ops.successNel
      }
    }
}
