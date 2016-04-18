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

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.Payloads
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples }

class GetRunsControllerSpec extends BaseRunsControllerSpec {

  s"GET '$baseEndpoint'" >> {
    br

    "when the database is empty" >> {
      br

      val ctx1 = HttpContext(() =>
        get(baseEndpoint, Seq(("userId", UserExamples.avg.id)),
          Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
      "when a verified user authenticates correctly" should ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

        "return status 200" in {
          http.rep.status mustEqual 200
        }

        "return an empty JSON list" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.jsonBody must haveSize(0)
        }
      }

      val ctx2 = HttpContext(() =>
        get(baseEndpoint, Seq(), Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
      "when the user ID is not specified" should ctx.priorReqsOnCleanDb(ctx2, populate = true) { http =>

        "return status 400" in {
          http.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
        }
      }

      val ctx3 = HttpContext(() =>
        get(baseEndpoint, Seq(("userId", UserExamples.avg.id)),
          Map(HeaderApiKey -> (UserExamples.avg.activeKey + "diff"))) { response })
      "when a verified user does not authenticate correctly" should
        ctx.priorReqsOnCleanDb(ctx3, populate = true) { http =>

          "return status 401" in {
            http.rep.status mustEqual 401
          }

          "return the authentication challenge header" in {
            http.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            http.rep.contentType mustEqual MimeType.Json
            http.rep.body must /("message" -> Payloads.AuthenticationError.message)
          }
        }

      val ctx4 = HttpContext(() =>
        get(baseEndpoint, Seq(("userId", UserExamples.unverified.id)),
          Map(HeaderApiKey -> UserExamples.unverified.activeKey)) { response })
      "when an unverified user authenticates correctly" should ctx.priorReqsOnCleanDb(ctx4, populate = true) { http =>

        "return status 403" in {
          http.rep.status mustEqual 403
        }

        "return a JSON object containing the expected message" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("message" -> Payloads.AuthorizationError.message)
        }
      }
    }

    "using the 'plain' and the 'maple' run summary files" >>
      ctx.priorReqsOnCleanDb(plainThenMapleContext, populate = true) { case http: UploadContext =>

        "after both summary files have been uploaded" in {
          http.reps.map(_.status).list mustEqual List(201, 201)
        }

        val ictx1 = HttpContext(() => get(baseEndpoint,
          Seq(), Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
        br; "when the user ID is not specified" should ctx.priorReqs(ictx1) { ihttp =>

        "return status 400" in {
          ihttp.reps.last.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.reps.last.contentType mustEqual MimeType.Json
          ihttp.reps.last.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
        }
      }

        val ictx2 = HttpContext(() => get(baseEndpoint, Seq(("userId", UserExamples.avg.id)),
          Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })
        "when a verified user does not authenticate correctly" should ctx.priorReqs(ictx2) { ihttp =>

          "return status 401" in {
            ihttp.reps.last.status mustEqual 401
          }

          "return the authentication challenge header" in {
            ihttp.reps.last.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            ihttp.reps.last.contentType mustEqual MimeType.Json
            ihttp.reps.last.body must /("message" -> Payloads.AuthenticationError.message)
          }
        }

        val ictx3 = HttpContext(() => get(baseEndpoint, Seq(("userId", UserExamples.unverified.id)),
          Map(HeaderApiKey -> UserExamples.unverified.activeKey)) { response })
        "when an unverified user authenticates correctly" should ctx.priorReqs(ictx3) { ihttp =>

          "return status 403" in {
            ihttp.reps.last.status mustEqual 403
          }

          "return a JSON object containing the expected message" in {
            ihttp.reps.last.contentType mustEqual MimeType.Json
            ihttp.reps.last.body must /("message" -> Payloads.AuthorizationError.message)
          }
        }

        "using a verified, authenticated user" >> {

          val iictx1 = HttpContext(() => get(baseEndpoint, Seq(("userId", UserExamples.avg.id)),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          br; "when using the default parameters" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 200" in {
              iihttp.reps.last.status mustEqual 200
            }

            "return a JSON object containing the expected message" in {
              iihttp.reps.last.contentType mustEqual MimeType.Json
              iihttp.reps.last.jsonBody must haveSize(1)
              iihttp.reps.last.body must /#(0) */("runId" -> """\S+""".r)
              iihttp.reps.last.body must /#(0) */("uploaderId" -> UserExamples.avg.id)
              iihttp.reps.last.body must /#(0) */("pipeline" -> "plain")
              iihttp.reps.last.body must /#(0) */("nSamples" -> 0)
              iihttp.reps.last.body must /#(0) */("nReadGroups" -> 0)
              iihttp.reps.last.body must not /("sampleIds" -> ".+".r)
              iihttp.reps.last.body must not /("readGroupIds" -> ".+".r)
            }
          }

          val iictx2 = HttpContext(() => get(baseEndpoint, Seq(("userId", UserExamples.avg.id), ("pipelines", "maple")),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          "when a pipeline he/she did not upload is selected" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 200" in {
              iihttp.reps.last.status mustEqual 200
            }

            "return an empty JSON list" in {
              iihttp.reps.last.contentType mustEqual MimeType.Json
              iihttp.reps.last.jsonBody must haveSize(0)
            }
          }

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", UserExamples.avg.id), ("pipelines", "nonexistent")),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          "when an incorrect pipeline parameter is used" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 400" in {
              iihttp.reps.last.status mustEqual 400
            }

            "return a JSON object containing the expected message" in {
              iihttp.reps.last.contentType mustEqual MimeType.Json
              iihttp.reps.last.body must /("message" -> "One or more pipeline is invalid.")
              iihttp.reps.last.body must /("hints") /# 0 / "invalid pipelines: nonexistent."
            }
          }
        }
      }
  }
}
