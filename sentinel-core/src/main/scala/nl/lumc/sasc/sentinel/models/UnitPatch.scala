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
package nl.lumc.sasc.sentinel.models

import org.bson.types.ObjectId

object UnitPatch {

  /** Helper object for patch operations on runs, samples, and read groups. */
  sealed trait OnUnit {
    def dbId: ObjectId
    def patchOps: Seq[JsonPatch.PatchOp]
  }

  /** Patch operations on a run record. */
  final case class OnRun(dbId: ObjectId, patchOps: List[JsonPatch.PatchOp]) extends OnUnit

  /** Patch operations on a sample record. */
  final case class OnSample(dbId: ObjectId, patchOps: List[JsonPatch.PatchOp]) extends OnUnit

  /** Patch operations on a read group record. */
  final case class OnReadGroup(dbId: ObjectId, patchOps: List[JsonPatch.PatchOp]) extends OnUnit

  /** Helper case class for unit patching. */
  final case class Combined(
      runPatches: List[UnitPatch.OnRun],
      samplePatches: List[UnitPatch.OnSample],
      readGroupPatches: List[UnitPatch.OnReadGroup]) {

    def ++(other: Combined) = Combined(
      runPatches ++ other.runPatches,
      samplePatches ++ other.samplePatches,
      readGroupPatches ++ other.readGroupPatches)

    /** Patch operations to apply for the run object. Note that one `Combined` object is meant for one run. */
    lazy val runPatchOps: List[JsonPatch.PatchOp] = runPatches.flatMap(_.patchOps)

    /** Patch operations to apply to samples of the run. */
    lazy val samplePatchOps: List[(ObjectId, List[JsonPatch.PatchOp])] = {
      val grouped = samplePatches.groupBy(_.dbId)
      samplePatches
        .map(_.dbId).distinct
        .map { id => (id, grouped.getOrElse(id, List()).flatMap(_.patchOps)) }
    }

    /** Patch operations to apply to read groups of the run. */
    lazy val readGroupPatchOps: List[(ObjectId, List[JsonPatch.PatchOp])] = {
      val grouped = readGroupPatches.groupBy(_.dbId)
      readGroupPatches
        .map(_.dbId).distinct
        .map { id => (id, grouped.getOrElse(id, List()).flatMap(_.patchOps)) }
    }
  }

  object Combined {
    def empty: Combined = Combined(List(), List(), List())
  }
}
