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
package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor
import nl.lumc.sasc.sentinel.processors.CompositeRunsProcessor
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

/**
 * Controller for the `/stats` endpoint.
 *
 */
abstract class BaseStatsController extends SentinelServlet
    with FutureSupport
    with AuthenticationSupport { self =>

  /** Object for accessing the database. */
  protected def mongo: MongodbAccessObject

  /** Container for main Swagger specification. */
  protected def swagger: Swagger

  /** Controller name, shown in the generated swagger spec */
  override protected val applicationName: Option[String] = Some("stats")

  /** Controller description, shown in the generated Swagger spec */
  protected val applicationDescription: String = "Statistics of deposited run summaries"

  /** Adapter for connecting to the run collections */
  protected val runs = new CompositeRunsProcessor(Seq(new PlainRunsProcessor(mongo)))

  /** Adapter for connecting to the users collection */
  protected val users = new UsersAdapter { val mongo = self.mongo }

  options("/runs") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  // format: OFF
  val runsGetOp = (apiOperation[Seq[PipelineStats]]("runsGet")
    summary "Retrieves general statistics of uploaded run summaries.")
  // format: ON

  get("/runs", operation(runsGetOp)) {
    logger.info(requestLog)
    new AsyncResult {
      val is = runs.getGlobalRunStats().map(res => Ok(res))
    }
  }
}
