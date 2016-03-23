/**
  * Copyright (c) 2016 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
  *
  * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
  */
package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.specification.core.Fragment
import scalaz.NonEmptyList

import nl.lumc.sasc.sentinel.exts.pann.PannRunsProcessor
import nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor
import nl.lumc.sasc.sentinel.testing.{ MimeType, UserExamples, SentinelServletSpec }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

/** Specifications for [[AnnotationsController]]. */
class AnnotationsControllerSpec extends SentinelServletSpec {

  val runsProcessorMakers = Set(
    (dao: MongodbAccessObject) => new PannRunsProcessor(dao),
    (dao: MongodbAccessObject) => new PlainRunsProcessor(dao))

  val annotsServlet = new AnnotationsController()(swagger, dao)
  val runsServlet = new RunsController()(swagger, dao, runsProcessorMakers)

  val baseEndpoint = "/annotations"
  addServlet(annotsServlet, s"$baseEndpoint/*")
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

    val ctx2 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Pann.Ann1))
    "using a summary file that contains annotation entries" >>
      ctx.priorReqsOnCleanDb(ctx2, populate = true) { http =>

        "after the run summary file is uploaded" in {
          http.rep.status mustEqual 201
        }

        val ictx1 = HttpContext(() => get(baseEndpoint) { response })
        br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON list containing 2 objects" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.jsonBody must haveSize(2)
          }

          "each of which" should {
            Fragment.foreach(0 to 1) { idx =>
              s"have the expected attributes (object #${idx + 1})" in {
                ihttp.rep.body must /#(idx) /("annotId" -> """\S+""".r)
                ihttp.rep.body must /#(idx) /("annotMd5" -> """\S+""".r)
              }
            }
          }
        }
    }; br

    val ctx3 = UploadContext(NonEmptyList(
      UploadSet(UserExamples.avg, SummaryExamples.Pann.Ann1),
      UploadSet(UserExamples.avg, SummaryExamples.Pann.Ann2)))
    "using multiple summary files that contain overlapping annotation entries" >>
      ctx.priorReqsOnCleanDb(ctx3, populate = true) { http =>

        "after the first run summary file is uploaded" in {
          http.rep.status mustEqual 201
        }

        "after the second run summary file is uploaded" in {
          http.reps.list(1).status mustEqual 201
        }

        val ictx1 = HttpContext(() => get(baseEndpoint) { response })
        br; "when using the default parameters" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON list containing 3 objects" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.jsonBody must haveSize(3)
          }

          "each of which" should {
            Fragment.foreach(0 to 2) { idx =>
              s"have the expected attributes (object #${idx + 1})" in {
                ihttp.rep.body must /#(idx) / ("annotId" -> """\S+""".r)
                ihttp.rep.body must /#(idx) / ("annotMd5" -> """\S+""".r)
              }
            }
          }
        }
    }
  }; br

  s"OPTIONS '$baseEndpoint/:annotId'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(s"$baseEndpoint/annotId", "GET,HEAD")
  }; br

  s"GET '$baseEndpoint/:annotId'" >> {
  br

    def endpoint(annotId: String) = s"$baseEndpoint/$annotId"

    val ctx1 = UploadContext(UploadSet(UserExamples.avg, SummaryExamples.Pann.Ann1))
    "using a run summary file that contains annotation entries" >>
      ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

        "after the run summary file is uploaded" in {
          http.rep.status mustEqual 201
        }

        val ictx1 = HttpContext(() => get(endpoint("yalala")) { response })
        br; "when an annotation entry with an invalid ID is queried" should ctx.priorReqs(ictx1) { ihttp =>

          "return status 404" in {
            ihttp.rep.status mustEqual 404
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Annotation ID can not be found.")
          }
        }

        val ictx2 = HttpContext(() => get(endpoint((parse(http.rep.body) \ "runId").extract[String])) { response })
        "when a nonexistent annotation entry is queried" should ctx.priorReqs(ictx2) { ihttp =>

          "return status 404" in {
            ihttp.rep.status mustEqual 404
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("message" -> "Annotation ID can not be found.")
          }
        }

        val annotId = (parse(http.rep.body) \ "annotIds").extract[Seq[String]].head
        val ictx3 = HttpContext(() => get(endpoint(annotId)) { response })
        "when an existing annotation entry is queried" should ctx.priorReqs(ictx3) { ihttp =>

          "return status 200" in {
            ihttp.rep.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            ihttp.rep.contentType mustEqual MimeType.Json
            ihttp.rep.body must /("annotId" -> """\S+""".r)
            ihttp.rep.body must /("annotMd5" -> """\S+""".r)
          }
        }
    }
  }
}
