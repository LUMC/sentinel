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
import scalaz.NonEmptyList

import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples, SentinelServletSpec }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class BaseStatsControllerSpec extends SentinelServletSpec {

  class TestBaseStatsController(implicit val swagger: Swagger, val mongo: MongodbAccessObject)
    extends BaseStatsController

  val runsProcessorMakers = Seq(
    (dao: MongodbAccessObject) => new nl.lumc.sasc.sentinel.exts.maple.MapleRunsProcessor(dao),
    (dao: MongodbAccessObject) => new nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor(dao))

  val statsServlet = new TestBaseStatsController()(swagger, dao)
  val runsServlet = new RunsController()(swagger, dao, runsProcessorMakers)

  val baseEndpoint = "/stats"
  addServlet(statsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"OPTIONS '$baseEndpoint/runs'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(s"$baseEndpoint/runs", "GET,HEAD")
  }; br

  s"GET '$baseEndpoint/runs'" >> {
  br

    val endpoint = s"$baseEndpoint/runs"

    val ctx1 = UploadContext(NonEmptyList(
      UploadSet(UserExamples.admin, SummaryExamples.Maple.SSampleMRG),
      UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleMRG),
      UploadSet(UserExamples.avg2, SummaryExamples.Plain),
      UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleSRG)))
    "using multiple summary files from 2 different pipelines uploaded by different users" >>
      ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

        "after all run summary files have been uploaded" in {
          http.reps.map(_.status).list mustEqual List.fill(http.reps.list.length)(201)
        }

        val ictx1 = HttpContext(() => get(endpoint) { response })
        br; "when using the default parameter" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON list containing 2 objects" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.jsonBody must haveSize(2)
          }

          "which" should {

            "contain statistics of the first pipeline" in {
              ihttp.rep.body must /#(0) /("pipelineName" -> "maple")
              ihttp.rep.body must /#(0) /("nReadGroups" -> 7)
              ihttp.rep.body must /#(0) /("nRuns" -> 3)
              ihttp.rep.body must /#(0) /("nSamples" -> 5)
            }

            "contain statistics of the second pipeline" in {
              ihttp.rep.body must /#(1) /("pipelineName" -> "plain")
              ihttp.rep.body must /#(1) /("nReadGroups" -> 0)
              ihttp.rep.body must /#(1) /("nRuns" -> 1)
              ihttp.rep.body must /#(1) /("nSamples" -> 0)
            }
          }
        }
      }
  }
}
