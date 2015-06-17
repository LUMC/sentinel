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
package nl.lumc.sasc.sentinel.processors.unsupported

import java.time.Clock
import java.util.Date
import nl.lumc.sasc.sentinel.models.{ RunRecord, User }
import scala.util.Try

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.Pipeline
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.utils.implicits._
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

/**
 * Input processor for generic run summary files.
 *
 * This input processor accepts any valid JSON files provided they are not empty. It does not store any samples and
 * libraries, nor does it store any references or annotations. Run summaries processed by this processor will not
 * contribute to the statistics database.
 *
 * @param mongo MongoDB database access object.
 */
class UnsupportedInputProcessor(protected val mongo: MongodbAccessObject)
    extends RunsAdapter
    with ValidationAdapter {

  val validator = createValidator("/schemas/unsupported.json")

  def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) =

    for {
      (byteContents, unzipped) <- Try(fi.readInputStream())
      _ <- Try(parseAndValidate(byteContents))
      fileId <- Try(storeFile(byteContents, user, pipeline, fi.getName, unzipped))
      run = RunRecord(fileId, user.id, pipeline.toString.toLowerCase, 0, 0, Date.from(Clock.systemUTC().instant))
      _ <- Try(storeRun(run))
    } yield run
}
