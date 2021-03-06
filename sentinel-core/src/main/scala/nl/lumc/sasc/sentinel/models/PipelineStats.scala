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

import com.novus.salat.annotations.Key

/**
 * Container for per-pipeline statistics.
 *
 * @param pipelineName Pipeline name.
 * @param nRuns Total number of runs of the pipeline.
 * @param nSamples Total number of samples from all runs of the pipeline.
 * @param nReadGroups Total number of read groups from all runs of the pipeline.
 */
case class PipelineStats(
  @Key("_id") pipelineName: String,
  nRuns:                    Int,
  nSamples:                 Int,
  nReadGroups:              Int
)
