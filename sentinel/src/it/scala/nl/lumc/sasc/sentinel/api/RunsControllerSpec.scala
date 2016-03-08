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

import org.json4s._
import org.specs2.specification.core.Fragments
import scalaz.NonEmptyList

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.{ Payloads, SinglePathPatch }
import nl.lumc.sasc.sentinel.testing.{ MimeType, SentinelServletSpec, UserExamples, VariableSizedPart }
import nl.lumc.sasc.sentinel.settings.{ MaxRunSummarySize, MaxRunSummarySizeMb }
import nl.lumc.sasc.sentinel.utils.reflect.makeDelayedProcessor

class RunsControllerSpec extends SentinelServletSpec {

  val runsProcessorMakers = Set(
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.maple.MapleRunsProcessor],
    makeDelayedProcessor[nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor])
  val servlet = new RunsController()(swagger, dao, runsProcessorMakers)
  val baseEndpoint = "/runs"
  addServlet(servlet, s"$baseEndpoint/*")

  /** Helper function to create commonly used upload context in this test spec. */
  def plainContext = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Plain))
  def mapleContext = UploadContext(UploadSet(UserExamples.avg2, SummaryExamples.Maple.MSampleMRG))
  def plainThenMapleContext = UploadContext(NonEmptyList(
    UploadSet(UserExamples.avg, SummaryExamples.Plain),
    UploadSet(UserExamples.avg2, SummaryExamples.Maple.MSampleMRG)))

  s"OPTIONS '$baseEndpoint'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(baseEndpoint, "GET,HEAD,POST")
  }; br

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

      val ictx12 = UploadContext(UploadSet(UserExamples.avg, VariableSizedPart(MaxRunSummarySize + 100, "plain")))
      s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB" should
        ctx.priorReqsOnCleanDb(ictx12, populate = true) { ihttp =>

          "return status 413" in {
            ihttp.rep.status mustEqual 413
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> s"Run summary exceeded maximum allowed size of $MaxRunSummarySizeMb MB.")
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
    }
  }

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

  s"OPTIONS '$baseEndpoint/:runId'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(s"$baseEndpoint/:runId", "DELETE,GET,HEAD,PATCH")
  }; br

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

  s"PATCH '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    val ctx1 = mapleContext
    "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ctx1, populate = true) { case http: UploadContext =>

      val ictx1 = HttpContext(() => patch(endpoint(http.runId),
        Seq(SinglePathPatch("replace", "/runName", "test")).toByteArray) { response })
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
      Seq(SinglePathPatch("replace", "/runName", "test")).toByteArray,
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

      val patches = Seq(SinglePathPatch("replace", "/runName", "patchedRunName"))

      val ictx1 = mapleContext
      "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ictx1, populate = true) { case ihttp: UploadContext =>

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

      "using a valid user" >> {
      br

        val userSets = Seq(
          ("an admin user", UserExamples.admin),
          ("a non-admin user to his/her own account", UserExamples.avg))

        Fragments.foreach(userSets) { case (utype, uobj) =>

          s"such as $utype" >> {
          br

            val uparams = Seq(("userId", uobj.id))
            val headers = Map(HeaderApiKey -> uobj.activeKey)
            val patches = Seq(SinglePathPatch("replace", "runName", "patchedRunName"))

            val ictx1 = mapleContext
            "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(ictx1, populate = true) { case ihttp: UploadContext =>

              val iictx1 = HttpContext(() => patch(endpoint(ihttp.runId), uparams, patches.toByteArray,
                Map(HeaderApiKey -> "wrongKey")) { response })
              br; "when the API key is incorrect" should ctx.priorReqsOnCleanDb(iictx1, populate = true) { iihttp =>

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

                val iictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
                  Seq(("userId", UserExamples.admin.id)),
                  Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
                "when the supposedly patched run is queried afterwards" should ctx.priorReqs(iictx1) { iiihttp =>
                  "return the run name unchanged" in {
                    iiihttp.rep.body must not / "labels" /("runName" -> "patchedRunName")
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
                "when the user record ID is not specified" should ctx.priorReqsOnCleanDb(iiictx1, populate = true) { ihttp =>

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
                "when the patch document is non-JSON" should ctx.priorReqsOnCleanDb(iiictx1, populate = true) { ihttp =>

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
                  Seq.empty[SinglePathPatch].toByteArray, headers) { response })
                "when the patch document is an empty list" should ctx.priorReqsOnCleanDb(iiictx1, populate = true) { ihttp =>

                  "return status 400" in {
                    ihttp.rep.status mustEqual 400
                  }

                  "return a JSON object containing the expected message" in {
                    ihttp.rep.contentType mustEqual MimeType.Json
                    ihttp.rep.body must /("message" -> Payloads.JsonValidationError.message)
                    ihttp.rep.body must /("hints") /# 0 / startWith("error: array is too short")
                  }
                }
              }

              val iictx4 = mapleContext
              "using a 'maple' run summary file" >> ctx.priorReqsOnCleanDb(iictx4, populate = true) { case iihttp: UploadContext =>

                val iiictx1 = HttpContext(() => patch(endpoint(iihttp.runId), uparams,
                  Array.empty[Byte], headers) { response })
                "when the patch document is empty" should ctx.priorReqsOnCleanDb(iiictx1, populate = true) { ihttp =>

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
                  Seq("yalala", SinglePathPatch("replace", "/password", "newPass123")).toByteArray, headers) { response })
                "when the patch document contains an invalid entry" should
                  ctx.priorReqsOnCleanDb(iiictx1, populate = true) { ihttp =>

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


            }
          }
        }
      }
    }
  }

}
