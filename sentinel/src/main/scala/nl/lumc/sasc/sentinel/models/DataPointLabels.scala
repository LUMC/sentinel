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

import org.bson.types.ObjectId

/**
 * Data point label.
 *
 * @param runId Database ID of the run in which this data point is contained.
 * @param runName Name of the run in which this data point is contained.
 * @param sampleName Name of the sample in which this data point is contained.
 * @param readGroupName Name of the read group in which this data points is contained.
 */
case class DataPointLabels(
  runId: ObjectId,
  runName: Option[String],
  sampleName: Option[String],
  readGroupName: Option[String])