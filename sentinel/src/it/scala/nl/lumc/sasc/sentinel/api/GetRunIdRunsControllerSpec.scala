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

import org.specs2.specification.core.Fragments

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.Payloads
import nl.lumc.sasc.sentinel.testing.{MimeType, UserExamples}

class GetRunIdRunsControllerSpec extends BaseRunsControllerSpec {

  s"GET '$baseEndpoint/:runId'" >> {
    br

    def endpoint(uploadedRunId: String) = s"$baseEndpoint/$uploadedRunId"

    "using the 'plain' and the 'maple' run summary files" >> {
      ctx.priorReqsOnCleanDb(plainThenMapleContext, populate = true) { case http: UploadContext =>

        "after both summary files have been uploaded" in {
          http.reps.map(_.status).list mustEqual List(201, 201)
        }

        val ictx1 = HttpContext(() => get(endpoint(http.runId),
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

        val ictx2 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", UserExamples.avg.id)),
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

        val ictx3 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", UserExamples.unverified.id)),
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

          val iictx1 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", UserExamples.avg.id)),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          br; "when a run he/she uploaded is queried" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("runId" -> http.runId)
              iihttp.rep.body must /("uploaderId" -> UserExamples.avg.id)
              iihttp.rep.body must /("pipeline" -> "plain")
              iihttp.rep.body must /("nSamples" -> 0)
              iihttp.rep.body must /("nReadGroups" -> 0)
              iihttp.rep.body must not /("sampleIds" -> ".+".r)
              iihttp.rep.body must not /("readGroupIds" -> ".+".r)
            }
          }

          "when he/she specifies a 'true' value for the download parameter" >> {
            br

            Fragments.foreach(Seq("yes", "true", "ok", "1")) { dlParam =>

              val iictx1 = HttpContext(() => get(endpoint(http.runId),
                Seq(("userId", UserExamples.avg.id), ("download", dlParam)),
                Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
              s"such as '$dlParam'" should ctx.priorReqs(iictx1) { iihttp =>

                "return status 200" in {
                  iihttp.rep.status mustEqual 200
                }

                "return the expected Content-Disposition header" in {
                  iihttp.rep.header must havePair("Content-Disposition" ->
                    ("attachment; filename=" + http.sets.head.payload.fileName))
                }

                "return the uploaded summary file" in {
                  iihttp.rep.contentType mustEqual MimeType.Binary
                  iihttp.rep.body mustEqual new String(http.sets.head.payload.content)
                }
              }
            }
          }

          "when he/she specifies a 'false' value for the download parameter" >> {
            br

            Fragments.foreach(Seq("no", "false", "none", "null", "nothing", "0")) { dlParam =>

              val iictx1 = HttpContext(() => get(endpoint(http.runId),
                Seq(("userId", UserExamples.avg.id), ("download", dlParam)),
                Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
              s"such as '$dlParam'" should ctx.priorReqs(iictx1) { iihttp =>

                "return status 200" in {
                  iihttp.rep.status mustEqual 200
                }

                "return a JSON object containing the run data" in {
                  iihttp.rep.contentType mustEqual MimeType.Json
                  iihttp.rep.body must /("runId" -> http.runId)
                  iihttp.rep.body must /("uploaderId" -> UserExamples.avg.id)
                  iihttp.rep.body must not /("sampleIds" -> ".+".r)
                  iihttp.rep.body must not /("readGroupIds" -> ".+".r)
                  iihttp.rep.body must /("nSamples" -> 0)
                  iihttp.rep.body must /("nReadGroups" -> 0)
                  iihttp.rep.body must /("pipeline" -> "plain")
                }
              }
            }
          }

          val iictx2 = HttpContext(() => get(endpoint(http.runIds.last), Seq(("userId", UserExamples.avg.id)),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          "when a run he/she did not upload is queried" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }

          val iictx3 = HttpContext(() => get(endpoint("invalidId"), Seq(("userId", UserExamples.avg.id)),
            Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
          "when using an invalid run ID" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }
        }

        "using an authenticated admin user" >> {

          val iictx1 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", UserExamples.admin.id)),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          br; "when a run he/she did not upload is queried" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("runId" -> http.runId)
              iihttp.rep.body must /("uploaderId" -> UserExamples.avg.id)
              iihttp.rep.body must /("pipeline" -> "plain")
              iihttp.rep.body must /("nSamples" -> 0)
              iihttp.rep.body must /("nReadGroups" -> 0)
              iihttp.rep.body must not /("sampleIds" -> ".+".r)
              iihttp.rep.body must not /("readGroupIds" -> ".+".r)
            }
          }

          "when he/she specifies a 'true' value for the download parameter" >> {
            br

            Fragments.foreach(Seq("yes", "true", "ok", "1")) { dlParam =>

              val iictx1 = HttpContext(() => get(endpoint(http.runId),
                Seq(("userId", UserExamples.admin.id), ("download", dlParam)),
                Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
              s"such as '$dlParam'" should ctx.priorReqs(iictx1) { iihttp =>

                "return status 200" in {
                  iihttp.rep.status mustEqual 200
                }

                "return the expected Content-Disposition header" in {
                  iihttp.rep.header must havePair("Content-Disposition" ->
                    ("attachment; filename=" + http.sets.head.payload.fileName))
                }

                "return the uploaded summary file" in {
                  iihttp.rep.contentType mustEqual MimeType.Binary
                  iihttp.rep.body mustEqual new String(http.sets.head.payload.content)
                }
              }
            }
          }

          "when he/she specifies a 'false' value for the download parameter" >> {
            br

            Fragments.foreach(Seq("no", "false", "none", "null", "nothing", "0")) { dlParam =>

              val iictx1 = HttpContext(() => get(endpoint(http.runId),
                Seq(("userId", UserExamples.admin.id), ("download", dlParam)),
                Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
              s"such as '$dlParam'" should ctx.priorReqs(iictx1) { iihttp =>

                "return status 200" in {
                  iihttp.rep.status mustEqual 200
                }

                "return a JSON object containing the run data" in {
                  iihttp.rep.contentType mustEqual MimeType.Json
                  iihttp.rep.body must /("runId" -> http.runId)
                  iihttp.rep.body must /("uploaderId" -> UserExamples.avg.id)
                  iihttp.rep.body must not /("sampleIds" -> ".+".r)
                  iihttp.rep.body must not /("readGroupIds" -> ".+".r)
                  iihttp.rep.body must /("nSamples" -> 0)
                  iihttp.rep.body must /("nReadGroups" -> 0)
                  iihttp.rep.body must /("pipeline" -> "plain")
                }
              }
            }
          }

          val iictx2 = HttpContext(() => get(endpoint("invalidId"), Seq(("userId", UserExamples.admin.id)),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when using an invalid run ID" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
            }
          }
        }
      }
    }
  }
}
