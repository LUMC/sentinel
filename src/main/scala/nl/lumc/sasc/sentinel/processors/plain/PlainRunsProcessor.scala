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
package nl.lumc.sasc.sentinel.processors.plain

import java.time.Clock
import java.util.Date
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models.{ RunRecord, User }
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.JsonValidationAdapter
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Input processor for generic run summary files.
 *
 * This input processor accepts any valid JSON files provided they are not empty. It does not store any samples and
 * libraries, nor does it store any references or annotations. Run summaries processed by this processor will not
 * contribute to the statistics database.
 *
 * @param mongo MongoDB database access object.
 */
class PlainRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo)
    with JsonValidationAdapter {

  def pipelineName = "plain"

  val validator = createValidator("/schemas/plain.json")

  def processRun(fi: FileItem, user: User) =

    for {
      (byteContents, unzipped) <- Future { fi.readInputStream() }
      _ <- Future { parseAndValidate(byteContents) }
      fileId <- Future { storeFile(byteContents, user, fi.getName, unzipped) }
      run = RunRecord(fileId, user.id, pipelineName, Date.from(Clock.systemUTC().instant))
      _ <- Future { storeRun(run) }
    } yield run
}
