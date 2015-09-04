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
package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

import nl.lumc.sasc.sentinel.CaseClass

/**
 * Trait for sequence statistics container.
 *
 * @tparam T Container for read-level statistics.
 */
@Salat trait SeqStatsLike[T <: CaseClass] { this: CaseClass =>

  /** Statistics of the first read. */
  val read1: T

  /** Statistics of the second read. */
  val read2: Option[T]

  /** Combined statistics of the first and second read. */
  val readAll: Option[_]
}

/**
 * Aggregated sequencing input statistics.
 *
 * @param read1 Aggregated statistics of the first read (if paired-end) or the only read (if single-end).
 * @param read2 Aggregated statistics of the second read. Only defined for paired-end inputs.
 * @param readAll Aggregated statistics of both reads. Only defined if there is at least a paired-end data point
 *                in aggregation.
 */
case class SeqStatsAggr[T <: CaseClass](read1: T, read2: Option[T] = None, readAll: Option[T] = None)
  extends SeqStatsLike[T]
