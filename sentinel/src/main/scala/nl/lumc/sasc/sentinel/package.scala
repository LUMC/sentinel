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
/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc

/** General package-level information */
package object sentinel {

  /** Current API version */
  val CurrentApiVersion = "0.2"

  /** Header API key */
  val HeaderApiKey = "X-Sentinel-Key"

  object settings {

    val MaxRunSummarySizeMb = 16

    val MaxRunSummarySize = MaxRunSummarySizeMb * 1024 * 1024

    val MinUserIdLength = 3

    val MinPasswordLength = 6

    /** Configuration key names. */
    val DbConfKey = "mongodb"
    val SentinelConfKey = "sentinel"
  }
}
