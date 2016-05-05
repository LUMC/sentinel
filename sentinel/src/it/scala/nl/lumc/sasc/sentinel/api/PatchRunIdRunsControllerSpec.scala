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
import nl.lumc.sasc.sentinel.models.{ JsonPatch, Payloads }
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples }

class PatchRunIdRunsControllerSpec extends BaseRunsControllerSpec {

  s"PATCH '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    val ctx1 = mapleContext
    "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ctx1, populate = true) { case http: UploadContext =>

      val ictx1 = HttpContext(() => patch(endpoint(http.runId),
        Seq(JsonPatch.ReplaceOp("/labels/runName", "test")).toByteArray) { response })
      "when the user ID is not specified" should ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

        "return status 400" in {
          ihttp.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.rep.contentType mustEqual MimeType.Json
          ihttp.rep.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
        }
      }

    }

    val ctx2 = HttpContext(() => patch(endpoint(""), Seq(("userId", UserExamples.avg2.id)),
      Seq(JsonPatch.ReplaceOp("/labels/runName", "test")).toByteArray,
      Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })
    "when the run ID is not specified" should ctx.priorReqsOnCleanDb(ctx2, populate = true) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> Payloads.UnspecifiedRunIdError.message)
      }
    }

    "using an unverified user" >> {
      br

      val ictx1 = mapleContext
      "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ictx1, populate = true) { case ihttp: UploadContext =>

        val patches = Seq(JsonPatch.ReplaceOp("/labels/runName", "patchedRunName"))

        val iictx1 = HttpContext(() => patch(endpoint(ihttp.runId), Seq(("userId", UserExamples.unverified.id)),
          patches.toByteArray, Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
        "when the API key is incorrect" should ctx.priorReqsOnCleanDb(iictx1, populate = true) { iihttp =>

          "return status 401" in {
            iihttp.rep.status mustEqual 401
          }

          "return the challenge response header key" in {
            iihttp.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            iihttp.rep.contentType mustEqual MimeType.Json
            iihttp.rep.body must /("message" -> "Authentication required to access resource.")
          }

          val iiictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
            Seq(("userId", UserExamples.admin.id)),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly patched run is queried afterwards" should ctx.priorReqs(iiictx1) { iiihttp =>
            "return the run name unchanged" in {
              iiihttp.rep.body must not / "labels" /("runName" -> "patchedRunName")
            }
          }
        }
      }
    }

    "using a valid user" >> {
      br

      val userSets = Seq(
        ("an admin user", UserExamples.admin),
        ("a non-admin user to his/her own account", UserExamples.avg2))

      Fragments.foreach(userSets) { case (utype, uobj) =>

        s"such as $utype" >> {
          br

          val uparams = Seq(("userId", uobj.id))
          val headers = Map(HeaderApiKey -> uobj.activeKey)
          val patches = Seq(JsonPatch.ReplaceOp("runName", "patchedRunName"))

          val ictx1 = mapleContext
          "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ictx1, populate = true) { case ihttp: UploadContext =>

            val iictx1 = HttpContext(() => patch(endpoint(ihttp.runId), uparams, patches.toByteArray,
              Map(HeaderApiKey -> "wrongKey")) { response })
            br; "when the API key is incorrect" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 401" in {
              iihttp.rep.status mustEqual 401
            }

            "return the challenge response header key" in {
              iihttp.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> "Authentication required to access resource.")
            }

            val iictx1 = HttpContext(() => get(endpoint(ihttp.runId),
              Seq(("userId", UserExamples.admin.id)),
              Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
            "when the supposedly patched run is queried afterwards" should ctx.priorReqs(iictx1) { iiihttp =>
              "return the run name unchanged" in {
                iiihttp.rep.body must /("labels") /("runName" -> "Maple_04")
                iiihttp.rep.body must not / "labels"  /("runName" -> "patchedRunName")
              }
            }
          }
          }

          "using the correct authentication" >> {
            br

            val iictx1 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx1, populate = true) { case iihttp: UploadContext =>

              val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), Seq(), patches.toByteArray,
                headers) { response })
              "when the user record ID is not specified" should ctx.priorReqs(iiictx1) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
                }
              }
            }

            val iictx2 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx2, populate = true) { case iihttp: UploadContext =>

              val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Array[Byte](10, 20, 30), headers) { response })
              "when the patch document is non-JSON" should ctx.priorReqs(iiictx1) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual "application/json"
                  ihttp.rep.body must /("message" -> "JSON is invalid.")
                  ihttp.rep.body must /("hints") /# 0 / "Invalid syntax."
                }
              }
            }

            val iictx3 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx3, populate = true) { case iihttp: UploadContext =>

              val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Seq.empty[JsonPatch.PatchOp].toByteArray, headers) { response })
              "when the patch document is an empty list" should ctx.priorReqs(iiictx1) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> "Invalid patch operation(s).")
                  ihttp.rep.body must /("hints") /# 0 / "Patch array can not be empty."
                }
              }
            }

            val iictx4 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx4, populate = true) { case iihttp: UploadContext =>

              val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Array.empty[Byte], headers) { response })
              "when the patch document is empty" should ctx.priorReqs(iiictx1) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> Payloads.JsonValidationError.message)
                  ihttp.rep.body must /("hints") /# 0 / "Nothing to parse."
                }
              }

            }

            val iictx5 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx5, populate = true) { case iihttp: UploadContext =>

              val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Seq("yalala", JsonPatch.ReplaceOp("/password", "newPass123")).toByteArray, headers) { response })
              "when the patch document contains an invalid entry" should ctx.priorReqs(iiictx1) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> "JSON is invalid.")
                  ihttp.rep.body must /("hints") /# 0 / startWith("error: instance failed to match")
                }
              }
            }

            val iictx6 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx6, populate = true) { case iihttp: UploadContext =>

              def iiictx1 = HttpContext(() => get(endpoint(iihttp.runId), Seq(("userId", UserExamples.avg2.id)),
                Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })

              "when the run is queried" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return JSON object containing the expected 'runName' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("labels") /("runName" -> "Maple_04")
                }
              }

              val iiictx2 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Seq(JsonPatch.ReplaceOp("/labels/runName", "patchedRunName")).toByteArray, headers) { response })
              "when 'runName' is patched with 'replace'" should ctx.priorReqs(iiictx2) { iiihttp =>

                "return status 204" in {
                  iiihttp.rep.status mustEqual 204
                }

                "return an empty body" in {
                  iiihttp.rep.body must beEmpty
                }
              }

              "when the patched run is queried afterwards" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return a JSON object with an updated 'runName' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("labels") /("runName" -> "patchedRunName")
                }
              }
            }

            val iictx7 = UploadContext(UploadSet(UserExamples.avg2, SummaryExamples.Maple.MSampleMRG, showUnitsLabels = true))
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx7, populate = true) { case iihttp: UploadContext =>

              lazy val sampleId = iihttp.sampleLabels
                .collect { case (sid, slabels) if slabels.get("sampleName") == Option("sampleA") => sid }
                .toSeq.head

              def iiictx1 = HttpContext(() => get(endpoint(iihttp.runId),
                Seq(("userId", UserExamples.avg2.id), ("showUnitsLabels", "true")),
                Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })

              "when the run is queried" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return JSON object containing the expected '/sampleLabels/*/sampleName/sampleA' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("sampleLabels") / sampleId /("sampleName" -> "sampleA")
                }
              }

              val iiictx2 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Seq(JsonPatch.ReplaceOp(s"/sampleLabels/$sampleId/sampleName", "notSampleA")).toByteArray,
                headers) { response })
              "when 'sampleName' is patched with 'replace'" should ctx.priorReqs(iiictx2) { iiihttp =>

                "return status 204" in {
                  iiihttp.rep.status mustEqual 204
                }

                "return an empty body" in {
                  iiihttp.rep.body must beEmpty
                }
              }

              "when the patched run is queried afterwards" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return a JSON object with an updated 'sampleName' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("sampleLabels") / sampleId /("sampleName" -> "notSampleA")
                }
              }
            }

            val iictx8 = UploadContext(UploadSet(UserExamples.avg2, SummaryExamples.Maple.MSampleMRG, showUnitsLabels = true))
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx8, populate = true) { case iihttp: UploadContext =>

              lazy val readGroupId = iihttp.readGroupLabels
                .collect { case (rgid, rglabels) if rglabels.get("readGroupName") == Option("rg1") => rgid }
                .toSeq.head

              def iiictx1 = HttpContext(() => get(endpoint(iihttp.runId),
                Seq(("userId", UserExamples.avg2.id), ("showUnitsLabels", "true")),
                Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })

              "when the run is queried" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return JSON object containing the expected '/readGroupLabels/*/readGroupName/rg1' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("readGroupLabels") / readGroupId /("readGroupName" -> "rg1")
                }
              }

              val iiictx2 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                Seq(JsonPatch.ReplaceOp(s"/readGroupLabels/$readGroupId/readGroupName", "rgA")).toByteArray,
                headers) { response })
              "when 'readGroupName' is patched with 'replace'" should ctx.priorReqs(iiictx2) { iiihttp =>

                "return status 204" in {
                  iiihttp.rep.status mustEqual 204
                }

                "return an empty body" in {
                  iiihttp.rep.body must beEmpty
                }
              }

              "when the patched run is queried afterwards" should ctx.priorReqs(iiictx1) { iiihttp =>

                "return status 200" in {
                  iiihttp.rep.status mustEqual 200
                }

                "return a JSON object with an updated 'readGroupName' attribute" in {
                  iiihttp.rep.contentType mustEqual MimeType.Json
                  iiihttp.rep.body must /("readGroupLabels") / readGroupId /("readGroupName" -> "rgA")
                }
              }
            }
          }
        }
      }
    }
  }
}
