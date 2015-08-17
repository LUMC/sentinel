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
package nl.lumc.sasc.sentinel.processors

import scala.concurrent.{ Future, ExecutionContext }

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models.{ GenericRunRecord, User }

/**
 * Input processor that does not process incoming runs. Rather, it is used for querying run data of any pipelines.
 */
class GenericRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo) {

  type RunRecord = GenericRunRecord

  implicit override protected def context: ExecutionContext = ExecutionContext.global

  def pipelineName = "generic"

  def processRun(fi: FileItem, user: User) = Future { throw new NotImplementedError }
}
