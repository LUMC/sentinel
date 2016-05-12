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

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.specification.core.Fragment
import scalaz.NonEmptyList

import nl.lumc.sasc.sentinel.models.ReferenceRecord
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples, SentinelServletSpec }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class ReferencesControllerSpec extends SentinelServletSpec {

  val runsProcessorMakers = Seq(
    (dao: MongodbAccessObject) => new nl.lumc.sasc.sentinel.exts.pref.PrefRunsProcessor(dao),
    (dao: MongodbAccessObject) => new nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor(dao))

  val refsServlet = new ReferencesController()(swagger, dao)
  val runsServlet = new RunsController()(swagger, dao, runsProcessorMakers)

  val baseEndpoint = "/references"
  addServlet(refsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"OPTIONS '$baseEndpoint'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(baseEndpoint, "GET,HEAD")
  }; br

  s"GET '$baseEndpoint'" >> {
  br

    val ctx1 = HttpContext(() => get(baseEndpoint) { response })
    "when the database is empty" should ctx.priorReqsOnCleanDb(ctx1) { http =>

      "return status 200" in {
        http.rep.status mustEqual 200
      }

      "return an empty JSON list" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.jsonBody must haveSize(0)
      }
    }

    val ctx2 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Pref.Ref1))
    "using a summary file that contain a reference entry" >>
      ctx.priorReqsOnCleanDb(ctx2, populate = true) { http =>

        "after the run summary file is uploaded" in {
          http.rep.status mustEqual 201
        }

        val ictx1 = HttpContext(() => get(baseEndpoint) {
          response
        })
        br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON list containing 1 object" in {
            ihttp.rep.contentType mustEqual "application/json"
            ihttp.rep.jsonBody must haveSize(1)
          }

          "which" should {
            s"have the expected attributes" in {
              ihttp.rep.body must /#(0) / ("refId" -> """\S+""".r)
              ihttp.rep.body must /#(0) / ("combinedMd5" -> """\S+""".r)
              ihttp.rep.jsonBody must beSome.like { case json =>
                (json(0) \ "contigs" \\ "md5").children
                  .map(_.extract[String]).size must beGreaterThan(0)
              }
            }
          }
        }
    }; br

    val ctx3 = UploadContext(NonEmptyList(
      UploadSet(UserExamples.avg, SummaryExamples.Pref.Ref1),
      UploadSet(UserExamples.admin, SummaryExamples.Pref.Ref2),
      UploadSet(UserExamples.avg, SummaryExamples.Pref.Ref3)))
    "using multiple summary files that contain ovelapping reference entries" >>
      ctx.priorReqsOnCleanDb(ctx3, populate = true) { http =>

        "after all the files have been uploaded" in {
          http.reps.list.map(_.status) mustEqual List.fill(http.reps.list.length)(201)
        }

        val ictx1 = HttpContext(() => get(baseEndpoint) { response })
        br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON list containing 2 object" in {
            ihttp.rep.contentType mustEqual "application/json"
            ihttp.rep.jsonBody must haveSize(2)
          }

          "each of which" should {
            Fragment.foreach(0 to 1) { idx =>
              s"have the expected attributes (object #${idx + 1})" in {
                ihttp.rep.body must /#(idx) /("refId" -> """\S+""".r)
                ihttp.rep.body must /#(idx) /("combinedMd5" -> """\S+""".r)
                ihttp.rep.jsonBody must beSome.like { case json =>
                  json(idx).extract[ReferenceRecord].contigs.size must beGreaterThan(0)
                }
              }
            }
          }
        }
      }
  }; br

  s"OPTIONS '$baseEndpoint/:refId'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(s"$baseEndpoint/refId", "GET,HEAD")
  }; br

  s"GET '$baseEndpoint/:refId'" >> {
  br

    def endpoint(refId: String) = s"$baseEndpoint/$refId"

    val ctx1 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Pref.Ref1))
    "using a run summary file that contains a reference entry" >>
      ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

        "after the run summary file is uploaded" in {
          http.rep.status mustEqual 201
        }

        val ictx1 = HttpContext(() => get(endpoint("yalala")) { response })
        br; "when a reference entry with an invalid ID is queried" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 404" in {
            ihttp.rep.status mustEqual 404
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Reference ID can not be found.")
          }
        }

        val ictx2 = HttpContext(() => get(endpoint((parse(http.rep.body) \ "runId").extract[String])) { response })
        "when a nonexistent reference entry is queried" should ctx.priorReqs(ictx2) { ihttp =>

          "return status 404" in {
            ihttp.rep.status mustEqual 404
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Reference ID can not be found.")
          }
        }

        val refId = (parse(http.rep.body) \ "refId").extract[String]
        val ictx3 = HttpContext(() => get(endpoint(refId)) { response })
        "when an existing reference entry is queried" should ctx.priorReqs(ictx3) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("refId" -> """\S+""".r)
            ihttp.rep.body must /("combinedMd5" -> """\S+""".r)
            ihttp.rep.jsonBody must beSome.like { case json =>
              (json \ "contigs" \\ "md5").children
                .map(_.extract[String]).size must beGreaterThan(0)
            }
          }
        }
      }
  }
}
