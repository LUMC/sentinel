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
import nl.lumc.sasc.sentinel.settings.DefaultMaxRunSummarySize
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples, VariableSizedPart }
import org.json4s._

import scalaz.NonEmptyList

class PostRunsControllerSpec extends BaseRunsControllerSpec {

  val MaxRunSummarySizeMb = DefaultMaxRunSummarySize / (1024 * 1024)

  s"POST '$baseEndpoint'" >> {
  br

    val ctx1 = HttpContext(() => post(baseEndpoint, Seq(("userId", UserExamples.avg.id))) { response })
    "when the pipeline is not not specified" should ctx.priorReqs(ctx1) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Pipeline not specified.")
      }
    }

    val ctx2 = HttpContext(
      () => post(baseEndpoint, Seq(("userId", UserExamples.avg.id), ("pipeline", "plain"))) { response })
    "when the request body is empty" should ctx.priorReqs(ctx2) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Run summary file not specified.")
      }
    }

    val ctx3 = HttpContext(() => post(baseEndpoint,
      Seq(("userId", UserExamples.avg.id), ("pipeline", "devtest")), Map("run" -> SummaryExamples.Plain)) { response })
    "when an invalid pipeline is specified" should ctx.priorReqs(ctx3) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Pipeline parameter is invalid.")
        http.rep.body must /("hints") /# 0 / "Valid values are .+".r
      }
    }

    "using the 'plain' pipeline summary file" >> {

      val ictx1 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Plain))
      br; "when a valid run summary is uploaded" should
        ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

          "return status 201" in {
            ihttp.rep.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("creationTimeUtc" -> ".+".r)
            ihttp.rep.body must /("nReadGroups" -> 0)
            ihttp.rep.body must /("nSamples" -> 0)
            ihttp.rep.body must /("pipeline" -> "plain")
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must /("uploaderId" -> ictx1.uploader.id)
            ihttp.rep.body must not /("annotIds" -> ".+".r)
            ihttp.rep.body must not /("refId" -> ".+".r)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
          }
      }

      val ictx2 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.PlainCompressed))
      "when a compressed valid run summary is uploaded" should
        ctx.priorReqsOnCleanDb(ictx2, populate = true) { ihttp =>

          "return status 201" in {
            ihttp.rep.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("creationTimeUtc" -> ".+".r)
            ihttp.rep.body must /("nReadGroups" -> 0)
            ihttp.rep.body must /("nSamples" -> 0)
            ihttp.rep.body must /("pipeline" -> "plain")
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must /("uploaderId" -> ictx2.uploader.id)
            ihttp.rep.body must not /("annotIds" -> ".+".r)
            ihttp.rep.body must not /("refId" -> ".+".r)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("libIds" -> ".+".r)
          }
        }

      val ictx3 = UploadContext(NonEmptyList(
        UploadSet(UserExamples.avg2, SummaryExamples.Plain),
        UploadSet(UserExamples.avg, SummaryExamples.Plain)))
      "when the same run summary is uploaded more than once by different users" should
        ctx.priorReqsOnCleanDb(ictx3, populate = true) { ihttp =>
          "return status 201 for the first upload" in {
            ihttp.reps.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            ihttp.reps.head.contentType mustEqual MimeType.Json
            ihttp.reps.head.body must /("creationTimeUtc" -> ".+".r)
            ihttp.reps.head.body must /("nReadGroups" -> 0)
            ihttp.reps.head.body must /("nSamples" -> 0)
            ihttp.reps.head.body must /("pipeline" -> "plain")
            ihttp.reps.head.body must /("runId" -> """\S+""".r)
            ihttp.reps.head.body must /("uploaderId" -> ictx3.uploaders.head.id)
            ihttp.reps.head.body must not /("annotIds" -> ".+".r)
            ihttp.reps.head.body must not /("refId" -> ".+".r)
            ihttp.reps.head.body must not /("sampleIds" -> ".+".r)
            ihttp.reps.head.body must not /("libIds" -> ".+".r)
          }

          "return status 201 for the second upload" in {
            ihttp.reps.last.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the second upload" in {
            ihttp.reps.last.contentType mustEqual MimeType.Json
            ihttp.reps.last.body must /("creationTimeUtc" -> ".+".r)
            ihttp.reps.last.body must /("nReadGroups" -> 0)
            ihttp.reps.last.body must /("nSamples" -> 0)
            ihttp.reps.last.body must /("pipeline" -> "plain")
            ihttp.reps.last.body must /("runId" -> """\S+""".r)
            ihttp.reps.last.body must /("uploaderId" -> ictx3.uploaders.last.id)
            ihttp.reps.last.body must not /("annotIds" -> ".+".r)
            ihttp.reps.last.body must not /("refId" -> ".+".r)
            ihttp.reps.last.body must not /("sampleIds" -> ".+".r)
            ihttp.reps.last.body must not /("libIds" -> ".+".r)
          }
        }

      val ictx4 = HttpContext(() =>
        post(baseEndpoint, Seq(("pipeline", "plain")), Map("run" -> SummaryExamples.Plain)) { response })
      "when the user ID is not specified" should ctx.priorReqs(ictx4) { ihttp =>

        "return status 400" in {
          ihttp.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.rep.contentType mustEqual MimeType.Json
          ihttp.rep.body must /("message" -> "User ID not specified.")
        }
      }

      val ictx5 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Not))
      "when a non-JSON file is uploaded" should ctx.priorReqsOnCleanDb(ictx5, populate = true) { ihttp =>

        "return status 400" in {
          ihttp.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.rep.contentType mustEqual MimeType.Json
          ihttp.rep.body must /("message" -> "JSON is invalid.")
          ihttp.rep.body must /("hints") /# 0 / "Invalid syntax."
        }
      }

      val ictx6 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Invalid))
      "when an invalid run summary file is uploaded" should ctx.priorReqsOnCleanDb(ictx6, populate = true) { ihttp =>

        "return status 400" in {
          ihttp.rep.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          ihttp.rep.contentType mustEqual MimeType.Json
          ihttp.rep.body must /("message" -> "JSON is invalid.")
          ihttp.rep.body must /("hints") /# 0 / startWith("error: instance failed to match")
        }
      }

      val ictx7 = UploadContext(NonEmptyList(
        UploadSet(UserExamples.avg, SummaryExamples.Plain),
        UploadSet(UserExamples.avg, SummaryExamples.Plain)))
      "when the same run summary is uploaded more than once by the same user" should
        ctx.priorReqsOnCleanDb(ictx7, populate = true) { ihttp =>

          "return status 201 for the first upload" in {
            ihttp.reps.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            ihttp.reps.head.contentType mustEqual MimeType.Json
            ihttp.reps.head.body must /("creationTimeUtc" -> ".+".r)
            ihttp.reps.head.body must /("nReadGroups" -> 0)
            ihttp.reps.head.body must /("nSamples" -> 0)
            ihttp.reps.head.body must /("pipeline" -> "plain")
            ihttp.reps.head.body must /("runId" -> """\S+""".r)
            ihttp.reps.head.body must /("uploaderId" -> ictx7.uploaders.head.id)
            ihttp.reps.head.body must not /("annotIds" -> ".+".r)
            ihttp.reps.head.body must not /("refId" -> ".+".r)
            ihttp.reps.head.body must not /("sampleIds" -> ".+".r)
            ihttp.reps.head.body must not /("libIds" -> ".+".r)
          }

          "return status 409 for second upload" in {
            ihttp.reps.last.status mustEqual 409
          }

          "return a JSON object containing the expected message for the second upload" in {
            ihttp.reps.last.contentType mustEqual MimeType.Json
            // capture the first uploaded runId
            ihttp.reps.head.jsonBody must beSome.like { case json =>
              val id = (json \ "runId").extract[String]
              ihttp.reps.last.body must /("message" -> "Run summary already uploaded.")
              ihttp.reps.last.body must /("hints") /# 0 / s"Existing ID: $id."
            }
          }
        }

      val ictx8 = UploadContext(NonEmptyList(
        UploadSet(UserExamples.avg, SummaryExamples.Plain),
        UploadSet(UserExamples.avg, SummaryExamples.PlainCompressed)))
      "when a run summary is uploaded more than once (uncompressed then compressed) by the same user" should
        ctx.priorReqsOnCleanDb(ictx8, populate = true) { ihttp =>

          "return status 201 for the first upload" in {
            ihttp.reps.head.status mustEqual 201
          }

          "return a JSON object of the uploaded run for the first upload" in {
            ihttp.reps.head.contentType mustEqual MimeType.Json
            ihttp.reps.head.body must /("creationTimeUtc" -> ".+".r)
            ihttp.reps.head.body must /("nReadGroups" -> 0)
            ihttp.reps.head.body must /("nSamples" -> 0)
            ihttp.reps.head.body must /("pipeline" -> "plain")
            ihttp.reps.head.body must /("runId" -> """\S+""".r)
            ihttp.reps.head.body must /("uploaderId" -> ictx8.uploaders.head.id)
            ihttp.reps.head.body must not /("annotIds" -> ".+".r)
            ihttp.reps.head.body must not /("refId" -> ".+".r)
            ihttp.reps.head.body must not /("sampleIds" -> ".+".r)
            ihttp.reps.head.body must not /("libIds" -> ".+".r)
          }

          "return status 409 for the second upload" in {
            ihttp.reps.last.status mustEqual 409
          }

          "return a JSON object containing the expected message for the second upload" in {
            ihttp.reps.last.contentType mustEqual MimeType.Json
            // capture the first uploaded runId
            ihttp.reps.head.jsonBody must beSome.like { case json =>
              val id = (json \ "runId").extract[String]
              ihttp.reps.last.body must /("message" -> "Run summary already uploaded.")
              ihttp.reps.last.body must /("hints") /# 0 / s"Existing ID: $id."
            }
          }
        }

      val ictx9 = HttpContext(() => post(baseEndpoint,
        Seq(("userId", UserExamples.avg.id), ("pipeline", "plain")), Map("run" -> SummaryExamples.Plain)) { response })
      s"when the user does not provide the '$HeaderApiKey' header" should
        ctx.priorReqsOnCleanDb(ictx9, populate = true) { ihttp =>

          "return status 401" in {
            ihttp.rep.status mustEqual 401
          }

          "return the challenge response header key" in {
            ihttp.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Authentication required to access resource.")
          }
        }

      val ictx10 = HttpContext(() => post(baseEndpoint,
        Seq(("userId", UserExamples.avg.id), ("pipeline", "plain")),
        Map("run" -> SummaryExamples.Plain), Map(HeaderApiKey -> UserExamples.avg2.activeKey)) { response })
      s"when the provided '$HeaderApiKey' does not match the one owned by the user" should
        ctx.priorReqsOnCleanDb(ictx10, populate = true) { ihttp =>

          "return status 401" in {
            ihttp.rep.status mustEqual 401
          }

          "return the challenge response header key" in {
            ihttp.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Authentication required to access resource.")
          }
        }

      val ictx11 = UploadContext(UploadSet(UserExamples.unverified, SummaryExamples.Plain))
      "when an unverified user uploads a run summary file" should
        ctx.priorReqsOnCleanDb(ictx11, populate = true ) { ihttp =>

          "return status 403" in {
            ihttp.rep.status mustEqual 403
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Unauthorized to access resource.")
          }
        }

      val ictx12 = UploadContext(UploadSet(UserExamples.avg, VariableSizedPart(DefaultMaxRunSummarySize + 100, "plain")))
      s"when the submitted run summary exceeds 16.00 MB" should
        ctx.priorReqsOnCleanDb(ictx12, populate = true) { ihttp =>

          "return status 413" in {
            ihttp.rep.status mustEqual 413
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> s"Run summary exceeded maximum allowed size of 16.00 MB.")
          }
        }
    }

    "using a pipeline summary file with samples and read groups ('maple')" >> {
    br

      val ictx1 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleMRG))
      "when a multi sample, multi read group summary is uploaded to an empty database" should
        ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

          "return status 201" in {
            ihttp.rep.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            ihttp.rep.contentType mustEqual "application/json"
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must /("uploaderId" -> ictx1.uploader.id)
            ihttp.rep.body must /("pipeline" -> "maple")
            ihttp.rep.body must /("nSamples" -> 2)
            ihttp.rep.body must /("nReadGroups" -> 3)
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("readGroupIds" -> ".+".r)
          }
        }

      val ictx2 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Maple.MSampleMRG, showUnitsLabels = true))
      "when a multi sample, multi read group summary is uploaded to an empty database and 'showUnitsLabels' is true" should
        ctx.priorReqsOnCleanDb(ictx2, populate = true) { ihttp =>

          "return status 201" in {
            ihttp.rep.status mustEqual 201
          }

          "return a JSON object of the uploaded run" in {
            ihttp.rep.contentType mustEqual "application/json"
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must /("uploaderId" -> ictx2.uploader.id)
            ihttp.rep.body must /("pipeline" -> "maple")
            ihttp.rep.body must /("nSamples" -> 2)
            ihttp.rep.body must /("nReadGroups" -> 3)
            ihttp.rep.body must /("runId" -> """\S+""".r)
            ihttp.rep.body must not /("sampleIds" -> ".+".r)
            ihttp.rep.body must not /("readGroupIds" -> ".+".r)
            ihttp.rep.body must /("sampleLabels") */ """\S+""".r  /("sampleName" -> """\S+""".r)
            ihttp.rep.body must /("readGroupLabels") */ """\S+""".r  /("readGroupName" -> """\S+""".r)
          }
        }
    }
  }
}
