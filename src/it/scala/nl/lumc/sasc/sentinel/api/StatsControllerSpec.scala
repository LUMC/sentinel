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
import org.scalatra.test.Uploadable

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.reflect.runsProcessorMaker

class StatsControllerSpec extends SentinelServletSpec { self =>

  class TestStatsController(implicit val swagger: Swagger, val mongo: MongodbAccessObject)
      extends StatsController

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04RunsProcessor],
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.plain.PlainRunsProcessor])
  val baseEndpoint = "/stats"
  val statsServlet = new TestStatsController
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

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"

        def makeUpload(uploader: User, uploaded: Uploadable, pipeline: String): Req = {
          val params = Seq(("userId", uploader.id), ("pipeline", pipeline))
          val headers = Map(HeaderApiKey -> uploader.activeKey)
          () => post(uploadEndpoint, params, Map("run" -> uploaded), headers) { response }
        }

        def upload1 = makeUpload(UserExamples.admin, LumcSummaryExamples.Gentrap.V04.SSampleMRG, "gentrap")
        def upload2 = makeUpload(UserExamples.avg, LumcSummaryExamples.Gentrap.V04.MSampleMRG, "gentrap")
        def upload3 = makeUpload(UserExamples.avg2, SummaryExamples.Plain, "plain")
        def upload4 = makeUpload(UserExamples.avg, LumcSummaryExamples.Gentrap.V04.MSampleSRG, "gentrap")

        def priorRequests = Seq(upload1, upload2, upload3, upload4)

        "after the first file is uploaded" in {
          priorResponses.head.status mustEqual 201
        }

        "after the second file is uploaded" in {
          priorResponses(1).status mustEqual 201
        }

        "after the third file is uploaded" in {
          priorResponses(2).status mustEqual 201
        }

        "after the fourth file is uploaded" in {
          priorResponses(3).status mustEqual 201
        }

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

              "contain statistics over the first pipeline" in {
                priorResponse.body must /#(0) /("pipelineName" -> "gentrap")
                priorResponse.body must /#(0) /("nReadGroups" -> 10)
                priorResponse.body must /#(0) /("nRuns" -> 3)
                priorResponse.body must /#(0) /("nSamples" -> 6)
              }

              "contain statistics over the second pipeline" in {
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
