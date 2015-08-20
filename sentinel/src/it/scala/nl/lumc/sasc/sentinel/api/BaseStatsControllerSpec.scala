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
package nl.lumc.sasc.sentinel.api

import org.scalatra.swagger.Swagger

import nl.lumc.sasc.sentinel.utils.MongodbAccessObject
import nl.lumc.sasc.sentinel.utils.reflect.makeDelayedProcessor

class BaseStatsControllerSpec extends SentinelServletSpec { self =>

  class TestBaseStatsController(implicit val swagger: Swagger, val mongo: MongodbAccessObject)
      extends BaseStatsController

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.maple.MapleRunsProcessor],
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor])
  val baseEndpoint = "/stats"
  val statsServlet = new TestBaseStatsController
  val runsServlet = new RunsController
  addServlet(statsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"OPTIONS '$baseEndpoint/runs'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint/runs", "GET,HEAD")
    }
  }

  s"GET '$baseEndpoint/runs'" >> {
    br

    val endpoint = s"$baseEndpoint/runs"

    "using multiple summary files from 2 different pipelines uploaded by different users" >> inline {

      new Context.PriorRunUploadClean {

        def upload1 = UploadSet(UserExamples.admin, SummaryExamples.Maple.SSampleMRG, "maple")
        def upload2 = UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleMRG, "maple")
        def upload3 = UploadSet(UserExamples.avg2, SummaryExamples.Plain, "plain")
        def upload4 = UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleSRG, "maple")

        def priorRequests = Seq(upload1, upload2, upload3, upload4).map(_.request)

        "when using the default parameter should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON list containing 2 objects" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(2)
            }

            "which" should {

              "contain statistics of the first pipeline" in {
                priorResponse.body must /#(0) /("pipelineName" -> "maple")
                priorResponse.body must /#(0) /("nReadGroups" -> 7)
                priorResponse.body must /#(0) /("nRuns" -> 3)
                priorResponse.body must /#(0) /("nSamples" -> 5)
              }

              "contain statistics of the second pipeline" in {
                priorResponse.body must /#(1) /("pipelineName" -> "plain")
                priorResponse.body must /#(1) /("nReadGroups" -> 0)
                priorResponse.body must /#(1) /("nRuns" -> 1)
                priorResponse.body must /#(1) /("nSamples" -> 0)
              }
            }
          }
        }
      }
    }
  }
}
