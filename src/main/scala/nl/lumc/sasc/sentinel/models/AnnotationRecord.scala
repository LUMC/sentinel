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

import java.util.Date

import com.novus.salat.annotations._
import org.bson.types.ObjectId

/**
 * Representation of an annotation file used in a pipeline run.
 *
 * @param annotId Database ID.
 * @param annotMd5 MD5 checksum of the annotation file.
 * @param extension Extension of the annotation file (lower case).
 * @param fileName Name of the annotation file.
 * @param creationTimeUtc UTC time when the annotation record was created.
 */
case class AnnotationRecord(
  @Key("_id") annotId: ObjectId,
  annotMd5: String,
  extension: Option[String],
  fileName: Option[String] = None,
  creationTimeUtc: Option[Date] = None)
