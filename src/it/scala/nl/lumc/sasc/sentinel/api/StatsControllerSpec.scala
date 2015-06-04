package nl.lumc.sasc.sentinel.api

import org.scalatra.test.{ ClientResponse, Uploadable }

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.SentinelServletSpec.SchemaExamples
import nl.lumc.sasc.sentinel.models.User

class StatsControllerSpec extends SentinelServletSpec {

  sequential

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val baseEndpoint = "/stats"
  val statsServlet = new StatsController
  val runsServlet = new RunsController
  addServlet(statsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

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

        def upload1 = makeUpload(Users.admin, SchemaExamples.Gentrap.V04.SSampleMLib, "gentrap")
        def upload2 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleMLib, "gentrap")
        def upload3 = makeUpload(Users.avg2, SchemaExamples.Unsupported, "unsupported")
        def upload4 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleSLib, "gentrap")

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
                priorResponse.body must /#(0) /("name" -> "gentrap")
                priorResponse.body must /#(0) /("nLibs" -> 10)
                priorResponse.body must /#(0) /("nRuns" -> 3)
                priorResponse.body must /#(0) /("nSamples" -> 6)
              }

              "contain statistics over the second pipeline" in {
                priorResponse.body must /#(1) /("name" -> "unsupported")
                priorResponse.body must /#(1) /("nLibs" -> 0)
                priorResponse.body must /#(1) /("nRuns" -> 1)
                priorResponse.body must /#(1) /("nSamples" -> 0)
              }
            }
          }
        }
      }
    }
  }

  class StatsAlnGentrapOkTests(val request: () => ClientResponse, val expNumItems: Int) extends Context.PriorRequests {

    def priorRequests = Seq(request)

    "return status 200" in {
      priorResponse.status mustEqual 200
    }

    s"return a JSON list containing $expNumItems objects" in {
      priorResponse.contentType mustEqual "application/json"
      priorResponse.jsonBody must haveSize(expNumItems)
    }

    "each of which" should {
      Range(0, expNumItems) foreach { idx =>
        val item = idx + 1
        s"have the expected attributes (object #$item)" in {
          priorResponse.body must /#(idx) /("nReads" -> bePositiveNum)
          priorResponse.body must /#(idx) /("pctChimeras" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nSingletons" -> bePositiveNum)
          priorResponse.body must /#(idx) /("maxInsertSize" -> bePositiveNum)
          priorResponse.body must /#(idx) /("medianInsertSize" -> bePositiveNum)
          priorResponse.body must /#(idx) /("stdevInsertSize" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nReadsAligned" -> bePositiveNum)
          priorResponse.body must /#(idx) /("rateReadsMismatch" -> bePositiveNum)
          priorResponse.body must /#(idx) /("rateIndel" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nBasesAligned" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nBasesUtr" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nBasesCoding" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nBasesIntron" -> bePositiveNum)
          priorResponse.body must /#(idx) /("nBasesIntergenic" -> bePositiveNum)
          priorResponse.body must /#(idx) /("median5PrimeBias" -> bePositiveNum)
          priorResponse.body must /#(idx) /("median3PrimeBias" -> bePositiveNum)
          // TODO: use raw JSON matchers when we upgrade specs2
          priorResponse.jsonBody must beSome.like { case json =>
            (json(idx) \ "normalizedTranscriptCoverage").extract[Seq[Long]].size must beGreaterThan(idx)
          }
        }
      }
    }
  }
  // FIXME: Since specs2 converts all JsonNumber to Doubles, we have to do the comparison as doubles as well
  def bePositiveNum = beGreaterThan(0: Double) ^^ { (t: String) => t.toDouble }

  s"GET '$baseEndpoint/alignments/gentrap'" >> {
    br

    val endpoint = s"$baseEndpoint/alignments/gentrap"

    "when an invalid accumulation level is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("accLevel", "yalala"))) { response }
        def priorRequests: Seq[Req] = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Accumulation level parameter is invalid.")
          priorResponse.body must /("data" -> "Valid values are .+".r)
        }
      }
    }

    "when an invalid library type is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("libType", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Library type parameter is invalid.")
          priorResponse.body must /("data" -> "Valid values are .+".r)
        }
      }
    }

    "using the gentrap v0.4 summary (2 samples, 1 library)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleSLib

        "when using the default parameter should" >> inline {

          new StatsAlnGentrapOkTests(() => get(endpoint) { response }, 2)
        }
      }
    }

    "using multiple gentrap files uploaded by different users" >> inline {

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"
        def pipeline = "gentrap"

        def makeUpload(uploader: User, uploaded: Uploadable): Req = {
          val params = Seq(("userId", uploader.id), ("pipeline", pipeline))
          val headers = Map(HeaderApiKey -> uploader.activeKey)
          () => post(uploadEndpoint, params, Map("run" -> uploaded), headers) { response }
        }

        def upload1 = makeUpload(Users.admin, SchemaExamples.Gentrap.V04.SSampleMLib)
        def upload2 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleMLib)
        def upload3 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleSLib)

        def priorRequests = Seq(upload1, upload2, upload3)

        "after the first file is uploaded" in {
          priorResponses.head.status mustEqual 201
        }

        "after the second file is uploaded" in {
          priorResponses(1).status mustEqual 201
        }

        "after the third file is uploaded" in {
          priorResponses(2).status mustEqual 201
        }

        "when using the default parameter should" >> inline {

          new StatsAlnGentrapOkTests(() => get(endpoint) { response }, 6)
        }

        "when accumulation level is set to 'lib' should" >> inline {

          new StatsAlnGentrapOkTests(() => get(endpoint, Seq(("accLevel", "lib"))) { response }, 10)
        }
      }
    }
  }

  class StatsSeqGentrapOkTests(val request: () => ClientResponse, val expNumItems: Int) extends Context.PriorRequests {

    def priorRequests = Seq(request)

    "return status 200" in {
      priorResponse.status mustEqual 200
    }

    s"return a JSON list containing $expNumItems objects" in {
      priorResponse.contentType mustEqual "application/json"
      priorResponse.jsonBody must haveSize(expNumItems)
    }

    "each of which" should {
      Range(0, expNumItems) foreach { idx =>
        val item = idx + 1
        s"have the expected attributes (object #$item)" in {
          // read 1
          priorResponse.body must /#(idx) */ "read1" /("nReads" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBases" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBasesA" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBasesT" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBasesG" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBasesC" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read1" /("nBasesN" -> bePositiveNum)
          // TODO: use raw JSON matchers when we upgrade specs2
          priorResponse.jsonBody must beSome.like { case json =>
            (json(idx) \ "read1" \ "nBasesByQual").extract[Seq[Long]].size must beGreaterThan(idx)
            (json(idx) \ "read1" \ "medianQualByPosition").extract[Seq[Long]].size must beGreaterThan(idx)
          }

          // read 2
          priorResponse.body must /#(idx) */ "read2" /("nReads" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBases" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBasesA" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBasesT" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBasesG" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBasesC" -> bePositiveNum)
          priorResponse.body must /#(idx) */ "read2" /("nBasesN" -> bePositiveNum)
          // TODO: use raw JSON matchers when we upgrade specs2
          priorResponse.jsonBody must beSome.like { case json =>
            (json(idx) \ "read2" \ "nBasesByQual").extract[Seq[Long]].size must beGreaterThan(idx)
            (json(idx) \ "read2" \ "medianQualByPosition").extract[Seq[Long]].size must beGreaterThan(idx)
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/sequences/gentrap'" >> {
    br

    val endpoint = s"$baseEndpoint/sequences/gentrap"

    "when an invalid library type is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("libType", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Library type parameter is invalid.")
          priorResponse.body must /("data" -> "Valid values are .+".r)
        }
      }
    }

    "when an invalid sequencing QC phase is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("qcPhase", "yalala"))) { response }
        def priorRequests: Seq[Req] = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Sequencing QC phase parameter is invalid.")
          priorResponse.body must /("data" -> "Valid values are .+".r)
        }
      }
    }

    "using the gentrap v0.4 summary (2 samples, 1 library)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleSLib

        "when using the default parameter should" >> inline {

          new StatsSeqGentrapOkTests(() => get(endpoint) { response }, 2)
        }
      }
    }

    "using multiple gentrap files uploaded by different users" >> inline {

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"
        def pipeline = "gentrap"

        def makeUpload(uploader: User, uploaded: Uploadable): Req = {
          val params = Seq(("userId", uploader.id), ("pipeline", pipeline))
          val headers = Map(HeaderApiKey -> uploader.activeKey)
          () => post(uploadEndpoint, params, Map("run" -> uploaded), headers) { response }
        }

        def upload1 = makeUpload(Users.admin, SchemaExamples.Gentrap.V04.SSampleMLib)
        def upload2 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleMLib)
        def upload3 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleSLib)

        def priorRequests = Seq(upload1, upload2, upload3)

        "after the first file is uploaded" in {
          priorResponses.head.status mustEqual 201
        }

        "after the second file is uploaded" in {
          priorResponses(1).status mustEqual 201
        }

        "after the third file is uploaded" in {
          priorResponses(2).status mustEqual 201
        }

        "when using the default parameter should" >> inline {

          new StatsSeqGentrapOkTests(() => get(endpoint) { response }, 10)
        }
      }
    }
  }
}
