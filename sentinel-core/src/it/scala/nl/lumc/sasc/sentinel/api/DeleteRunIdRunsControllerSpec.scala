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

import scalaz.NonEmptyList

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.Payloads
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples }

class DeleteRunIdRunsControllerSpec extends BaseRunsControllerSpec {

  s"DELETE '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    "using the 'plain' run summary file" >>
      ctx.priorReqsOnCleanDb(plainContext, populate = true) { case http: UploadContext =>

        val ictx1 = HttpContext(() => delete(endpoint(""),
          Seq(("userId", http.uploader.id)),
          Map(HeaderApiKey -> http.uploader.activeKey)) { response })
        br; "when the run ID is not specified" should ctx.priorReqs(ictx1) { ihttp =>

        "return status 400" in {
          ihttp.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.rep.contentType mustEqual MimeType.Json
          ihttp.rep.body must /("message" -> Payloads.UnspecifiedRunIdError.message)
        }

        val iictx1 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", http.uploader.id)),
          Map(HeaderApiKey -> http.uploader.activeKey)) { response })
        br; "when the uploaded run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

        "return status 200" in {
          iihttp.rep.status mustEqual 200
        }

        "return a non-deleted JSON object of the run" in {
          iihttp.rep.contentType mustEqual MimeType.Json
          iihttp.rep.body must /("runId" -> """\S+""".r)
          iihttp.rep.body must not / ("deletionTimeUtc" -> ".+".r)
        }
      }

        val iictx2 = HttpContext(() => get(endpoint(http.runId),
          Seq(("userId", http.uploader.id), ("download", "true")),
          Map(HeaderApiKey -> http.uploader.activeKey)) { response })
        "when the uploaded run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

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

        val iictx3 = HttpContext(() => get(baseEndpoint,
          Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
        "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

          "return status 200" in {
            iihttp.rep.status mustEqual 200
          }

          "return JSON array containing all the runs" in {
            iihttp.rep.contentType mustEqual MimeType.Json
            iihttp.rep.jsonBody must haveSize(1)
            iihttp.rep.body must /#(0) /("runId" -> http.runId)
            iihttp.rep.body must not /# 0 /("deletionTimeUtc" -> ".+".r)
          }
        }
      }

        val ictx2 = HttpContext(() =>
          delete(endpoint(http.runId), Seq(), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
        "when the user ID is not specified" should ctx.priorReqs(ictx2) { ihttp =>

          "return status 400" in {
            ihttp.rep.status mustEqual 400
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
          }

          val iictx1 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", http.uploader.id)),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          br; "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

          "return status 200" in {
            iihttp.rep.status mustEqual 200
          }

          "return a non-deleted JSON object of the run" in {
            iihttp.rep.contentType mustEqual MimeType.Json
            iihttp.rep.body must /("runId" -> """\S+""".r)
            iihttp.rep.body must not / ("deletionTimeUtc" -> ".+".r)
          }
        }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id), ("download", "true")),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

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

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return JSON array containing the supposedly deleted run" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(1)
              iihttp.rep.body must /#(0) /("runId" -> http.runId)
              iihttp.rep.body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }

        val ictx3 = HttpContext(() => delete(endpoint(http.runId),
          Seq(("userId", http.uploader.id)),
          Map(HeaderApiKey -> (http.uploader.activeKey + "wrong"))) { response })
        "when a verified user authenticates incorrectly" should ctx.priorReqs(ictx3) { ihttp =>

          "return status 401" in {
            ihttp.rep.status mustEqual 401
          }

          "return the authentication challenge header" in {
            ihttp.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> Payloads.AuthenticationError.message)
          }

          val iictx1 = HttpContext(() => get(endpoint(http.runId), Seq(("userId", http.uploader.id)),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          br; "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

          "return status 200" in {
            iihttp.rep.status mustEqual 200
          }

          "return a non-deleted JSON object of the run" in {
            iihttp.rep.contentType mustEqual MimeType.Json
            iihttp.rep.body must /("runId" -> """\S+""".r)
            iihttp.rep.body must not / ("deletionTimeUtc" -> ".+".r)
          }
        }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id), ("download", "true")),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

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

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return JSON array containing the supposedly deleted run" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(1)
              iihttp.rep.body must /#(0) /("runId" -> http.runId)
              iihttp.rep.body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }
      }

    "using a verified, authenticated user" >> {
      br

      "using the 'plain' run summary file" >>
        ctx.priorReqsOnCleanDb(plainContext, populate = true) { case http: UploadContext =>

          val ictx1 = HttpContext(() => delete(endpoint(http.runId),
            Seq(("userId", UserExamples.avg2.id)), Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })
          br; "when he/she tries to delete a run he/she did not upload" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 404" in {
            ihttp.rep.status mustEqual 404
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
          }
        }
        }

      "using the 'plain' run summary file" >>
        ctx.priorReqsOnCleanDb(plainContext, populate = true) { case http: UploadContext =>

          def ictx1 = HttpContext(() => delete(endpoint(http.runId),
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object of the run data with the `deletionTimeUtc` attribute" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("runId" -> http.runId)
            ihttp.rep.body must /("uploaderId" -> http.uploader.id)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
            ihttp.rep.body must /("nSamples" -> 0)
            ihttp.rep.body must /("nReadGroups" -> 0)
            ihttp.rep.body must /("pipeline" -> "plain")
            ihttp.rep.body must /("deletionTimeUtc" -> ".+".r)
          }; br

          val iictx1 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id), ("download", "true")),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return an empty JSON array" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(0)
            }
          }

          "when the DELETE request is repeated" should ctx.priorReqs(ictx1) { iihttp =>

            "return status 410" in {
              iihttp.rep.status mustEqual 410
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.ResourceGoneError.message)
            }
          }
        }
        }

      def mapleThenPlainContext = UploadContext(NonEmptyList(
        UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleMRG),
        UploadSet(UserExamples.avg, SummaryExamples.Plain)))

      "using the 'maple' and 'plain' run summary files" >>
        ctx.priorReqsOnCleanDb(mapleThenPlainContext, populate = true) { case http: UploadContext =>

          def ictx1 = HttpContext(() => delete(endpoint(http.runId),
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          br; "when using the default parameters on the 'maple' upload" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object of the run data with the `deletionTimeUtc` attribute" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("runId" -> http.runId)
            ihttp.rep.body must /("uploaderId" -> http.uploader.id)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
            ihttp.rep.body must /("nSamples" -> 2)
            ihttp.rep.body must /("nReadGroups" -> 3)
            ihttp.rep.body must /("pipeline" -> "maple")
            ihttp.rep.body must /("deletionTimeUtc" -> ".+".r)
          }; br

          val iictx1 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", http.uploader.id), ("download", "true")),
            Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", http.uploader.id)), Map(HeaderApiKey -> http.uploader.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return a JSON array containing only the remaining pipelines" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(1)
              iihttp.rep.body must /#(0) /("runId" -> http.runIds.last)
              iihttp.rep.body must /#(0) /("pipeline" -> "plain")
              iihttp.rep.body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }

          "when the DELETE request is repeated" should ctx.priorReqs(ictx1) { iihttp =>

            "return status 410" in {
              iihttp.rep.status mustEqual 410
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.ResourceGoneError.message)
            }
          }
        }
        }
    }

    "using a verified, authenticated admin user" >> {
      br

      "using the 'plain' run summary file he/she did not upload" >>
        ctx.priorReqsOnCleanDb(plainContext, populate = true) { case http: UploadContext =>

          def ictx1 = HttpContext(() => delete(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object of the run data with the `deletionTimeUtc` attribute" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("runId" -> http.runId)
            ihttp.rep.body must /("uploaderId" -> http.uploader.id)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
            ihttp.rep.body must /("nSamples" -> 0)
            ihttp.rep.body must /("nReadGroups" -> 0)
            ihttp.rep.body must /("pipeline" -> "plain")
            ihttp.rep.body must /("deletionTimeUtc" -> ".+".r)
          }; br

          val iictx1 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id), ("download", "true")),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return an empty JSON array" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(0)
            }
          }

          "when the DELETE request is repeated" should ctx.priorReqs(ictx1) { iihttp =>

            "return status 410" in {
              iihttp.rep.status mustEqual 410
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.ResourceGoneError.message)
            }
          }
        }
        }

      def mapleThenPlainContext = UploadContext(NonEmptyList(
        UploadSet(UserExamples.avg2, SummaryExamples.Maple.MSampleMRG),
        UploadSet(UserExamples.avg, SummaryExamples.Plain)))

      "using the 'maple' and 'plain' run summary files he/she did not upload" >>
        ctx.priorReqsOnCleanDb(mapleThenPlainContext, populate = true) { case http: UploadContext =>

          def ictx1 = HttpContext(() => delete(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          br; "when using the default parameters on the 'maple' upload" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object of the run data with the `deletionTimeUtc` attribute" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("runId" -> http.runId)
            ihttp.rep.body must /("uploaderId" -> http.uploader.id)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
            ihttp.rep.body must /("nSamples" -> 2)
            ihttp.rep.body must /("nReadGroups" -> 3)
            ihttp.rep.body must /("pipeline" -> "maple")
            ihttp.rep.body must /("deletionTimeUtc" -> ".+".r)
          }; br

          val iictx1 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly deleted run is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx2 = HttpContext(() => get(endpoint(http.runId),
            Seq(("userId", UserExamples.admin.id), ("download", "true")),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly deleted run is downloaded afterwards" should ctx.priorReqs(iictx2) { iihttp =>

            "return status 404" in {
              iihttp.rep.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.RunIdNotFoundError.message)
              iihttp.rep.body must not /("runId" -> ".+".r)
            }
          }

          val iictx3 = HttpContext(() => get(baseEndpoint,
            Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the run collection listing is queried afterwards" should ctx.priorReqs(iictx3) { iihttp =>

            "return status 200" in {
              iihttp.rep.status mustEqual 200
            }

            "return a JSON array containing only the remaining pipelines" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.jsonBody must haveSize(1)
              iihttp.rep.body must /#(0) /("runId" -> http.runIds.last)
              iihttp.rep.body must /#(0) /("pipeline" -> "plain")
              iihttp.rep.body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }

          "when the DELETE request is repeated" should ctx.priorReqs(ictx1) { iihttp =>

            "return status 410" in {
              iihttp.rep.status mustEqual 410
            }

            "return a JSON object containing the expected message" in {
              iihttp.rep.contentType mustEqual MimeType.Json
              iihttp.rep.body must /("message" -> Payloads.ResourceGoneError.message)
            }
          }
        }
        }
    }
  }

}
