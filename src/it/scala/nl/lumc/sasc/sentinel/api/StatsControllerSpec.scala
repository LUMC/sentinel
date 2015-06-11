package nl.lumc.sasc.sentinel.api

import org.scalatra.test.{ ClientResponse, Uploadable }

import org.json4s._
import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.User

class StatsControllerSpec extends SentinelServletSpec {

  import SentinelServletSpec.SchemaExamples

  sequential

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val baseEndpoint = "/stats"
  val statsServlet = new StatsController
  val runsServlet = new RunsController
  addServlet(statsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  // FIXME: Since specs2 converts all JsonNumber to Doubles, we have to do the comparison as doubles as well
  def bePositiveNum = beGreaterThan(0: Double) ^^ { (t: String) => t.toDouble }

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

    def priorRequests = Stream(request)

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
        // proxy for determining whether the stats contain paired-end reads or not
        // FIXME: nicer way to do this?
        lazy val pairedEnd = priorResponse.jsonBody match {
          case Some(jv) => (jv(idx) \ "pctChimeras" \\ classOf[JDouble]).nonEmpty
          case None => false
        }
        s"have the expected attributes (object #$item)" in {
          priorResponse.body must /#(idx) /("nReads" -> bePositiveNum)
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
          priorResponse.body must /#(idx) /("pctChimeras" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) /("nSingletons" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) /("maxInsertSize" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) /("medianInsertSize" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) /("stdevInsertSize" -> bePositiveNum) iff pairedEnd
          // TODO: use raw JSON matchers when we upgrade specs2
          priorResponse.jsonBody must beSome.like { case json =>
            (json(idx) \ "normalizedTranscriptCoverage").extract[Seq[Long]].size must beGreaterThan(idx)
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/gentrap/alignments'" >> {
    br

    val endpoint = s"$baseEndpoint/gentrap/alignments"

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

    "when an invalid run ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("runIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid run ID(s) provided.")
        }
      }
    }

    "when an invalid reference ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("refIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid reference ID(s) provided.")
        }
      }
    }

    "when an invalid annotation ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("annotIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid annotation ID(s) provided.")
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

    "using the gentrap v0.4 summary (3 samples, 6 library, mixed library types)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleMLibMixedLib

        "when using the default parameter should" >> inline {

          new StatsAlnGentrapOkTests(() => get(endpoint) { response }, 3)
        }

        "when queried with accLevel set to 'sample'" >> {
          br

          val accLevel = "sample"

          "and libType not set should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel))) { response }, 3)
          }

          "and libType set to 'single' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel), ("libType", "single"))) { response }, 3)
          }

          "and libType set to 'paired' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel), ("libType", "paired"))) { response }, 3)
          }
        }

        "when queried with accLevel set to 'lib'" >> {
          br

          val accLevel = "lib"

          "and libType not set should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel))) { response }, 6)
          }

          "and libType set to 'single' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel), ("libType", "single"))) { response }, 4)
          }

          "and libType set to 'paired' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("accLevel", accLevel), ("libType", "paired"))) { response }, 2)
          }
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
        def upload2 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleMLibMixedLib)
        def upload3 = makeUpload(Users.avg, SchemaExamples.Gentrap.V04.MSampleSLib)

        def priorRequests = Stream(upload1, upload2, upload3)

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

        "when run IDs is set" >> {
          br

          lazy val runId1 = (parse(priorResponses(0).body) \ "runId").extract[String]
          lazy val runId2 = (parse(priorResponses(1).body) \ "runId").extract[String]
          lazy val runIds = Seq(runId1, runId2).mkString(",")

          "and other parameters are not set should" >> inline {

            new StatsAlnGentrapOkTests(() => get(endpoint, Seq(("runIds", runIds))) { response }, 4)
          }

          "and accumulation level is set to 'lib' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("runIds", runIds), ("accLevel", "lib"))) { response }, 8)
          }
        }

        "when reference IDs is set" >> {
          br

          lazy val refIds = Seq((parse(priorResponses(1).body) \ "refId").extract[String]).mkString(",")

          "and other parameters are not set should" >> inline {

            new StatsAlnGentrapOkTests(() => get(endpoint, Seq(("refIds", refIds))) { response }, 3)
          }

          "and accumulation level is set to 'lib' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("refIds", refIds), ("accLevel", "lib"))) { response }, 6)
          }
        }

        "when annotation IDs is set" >> {
          br

          lazy val annotIds = (parse(priorResponses(1).body) \ "annotIds").extract[Seq[String]].mkString(",")

          "and other parameters are not set should" >> inline {

            new StatsAlnGentrapOkTests(() => get(endpoint, Seq(("annotIds", annotIds))) { response }, 3)
          }

          "and accumulation level is set to 'lib' should" >> inline {

            new StatsAlnGentrapOkTests(
              () => get(endpoint, Seq(("annotIds", annotIds), ("accLevel", "lib"))) { response }, 6)
          }
        }

        "when queried multiple times using the default parameter should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Stream.fill(10)(request)

            "return the items in random order" in {
              // if the items are returned in the same order, the 'body' string will be the same so the set size == 1
              priorResponses.map(_.body).distinct.size must beGreaterThan(1)
            }
          }
        }

        "when queried multiple times with sorted set to 'yes' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("sorted", "yes"))) { response }
            def priorRequests = Stream.fill(10)(request)

            "return the items in the nonrandom order" in {
              priorResponses.map(_.body).distinct.size mustEqual 1
            }
          }
        }

        "when queried multiple times with sorted set to 'yes' and accLevel set to 'lib' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("sorted", "yes"), ("accLevel", "lib"))) { response }
            def priorRequests = Stream.fill(10)(request)

            "return the items in the nonrandom order" in {
              priorResponses.map(_.body).distinct.size mustEqual 1
            }
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/gentrap/alignments/aggregates'" >> {
    br

    val endpoint = s"$baseEndpoint/gentrap/alignments/aggregates"

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

    "when an invalid run ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("runIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid run ID(s) provided.")
        }
      }
    }

    "when an invalid reference ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("refIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid reference ID(s) provided.")
        }
      }
    }

    "when an invalid annotation ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("annotIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid annotation ID(s) provided.")
        }
      }
    }

    "when the database is empty should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint) { response }
        def priorRequests = Seq(request)

        "return status 404" in  {
          priorResponse.status mustEqual 404
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "No data points for aggregation found.")
        }
      }
    }

    "using the gentrap v0.4 summary (3 samples, 6 library, mixed library types)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleMLibMixedLib

        "when using the default parameter should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              val nSingleSample = 2
              val nPairedSample = 1
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("nReads") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nReadsAligned") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("rateReadsMismatch") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("rateIndel") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nBasesAligned") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nBasesUtr") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nBasesCoding") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nBasesIntron") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("nBasesIntergenic") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleSample + nPairedSample))
              priorResponse.body must /("pctChimeras") /("count" -> nPairedSample)
              priorResponse.body must /("nSingletons") /("count" -> nPairedSample)
              priorResponse.body must /("maxInsertSize") /("count" -> nPairedSample)
              priorResponse.body must /("medianInsertSize") /("count" -> nPairedSample)
              priorResponse.body must /("stdevInsertSize") /("count" -> nPairedSample)
            }
          }
        }

        "when accLevel is set to 'lib' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("accLevel", "lib"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              val nSingleLib = 4
              val nPairedLib = 2
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("nReads") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nReadsAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateReadsMismatch") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateIndel") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesUtr") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesCoding") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntron") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntergenic") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("pctChimeras") /("count" -> nPairedLib)
              priorResponse.body must /("nSingletons") /("count" -> nPairedLib)
              priorResponse.body must /("maxInsertSize") /("count" -> nPairedLib)
              priorResponse.body must /("medianInsertSize") /("count" -> nPairedLib)
              priorResponse.body must /("stdevInsertSize") /("count" -> nPairedLib)
            }
          }
        }

        "when accLevel is set to 'lib' and libType set to 'paired' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("accLevel", "lib"), ("libType", "paired"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              val nSingleLib = 0
              val nPairedLib = 2
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("nReads") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nReadsAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateReadsMismatch") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateIndel") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesUtr") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesCoding") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntron") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntergenic") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("pctChimeras") /("count" -> nPairedLib)
              priorResponse.body must /("nSingletons") /("count" -> nPairedLib)
              priorResponse.body must /("maxInsertSize") /("count" -> nPairedLib)
              priorResponse.body must /("medianInsertSize") /("count" -> nPairedLib)
              priorResponse.body must /("stdevInsertSize") /("count" -> nPairedLib)
            }
          }
        }

        "when accLevel is set to 'lib' and libType set to 'single' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("accLevel", "lib"), ("libType", "single"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              val nSingleLib = 4
              val nPairedLib = 0
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("nReads") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nReadsAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateReadsMismatch") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("rateIndel") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesAligned") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesUtr") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesCoding") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntron") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("nBasesIntergenic") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
              priorResponse.body must /("median5PrimeBias") /("count" -> (nSingleLib + nPairedLib))
            }
          }
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
        // proxy for determining whether the stats contain paired-end reads or not
        // FIXME: nicer way to do this?
        lazy val pairedEnd = priorResponse.jsonBody match {
          case Some(jv) => (jv(idx) \ "read2") != JNothing
          case None => false
        }
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
          priorResponse.body must /#(idx) */ "read2" /("nReads" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBases" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBasesA" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBasesT" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBasesG" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBasesC" -> bePositiveNum) iff pairedEnd
          priorResponse.body must /#(idx) */ "read2" /("nBasesN" -> bePositiveNum) iff pairedEnd
          // TODO: use raw JSON matchers when we upgrade specs2
          priorResponse.jsonBody must beSome.like { case json =>
            (json(idx) \ "read2" \ "nBasesByQual").extract[Seq[Long]].size must beGreaterThan(idx) iff pairedEnd
            (json(idx) \ "read2" \ "medianQualByPosition").extract[Seq[Long]].size must beGreaterThan(idx) iff pairedEnd
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/gentrap/sequences'" >> {
    br

    val endpoint = s"$baseEndpoint/gentrap/sequences"

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

    "when an invalid run ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("runIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid run ID(s) provided.")
        }
      }
    }

    "when an invalid reference ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("refIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid reference ID(s) provided.")
        }
      }
    }

    "when an invalid annotation ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("annotIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid annotation ID(s) provided.")
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

    "using the gentrap v0.4 summary (3 samples, 6 library, mixed library types)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleMLibMixedLib

        "when using the default parameter should" >> inline {

          new StatsSeqGentrapOkTests(() => get(endpoint) { response }, 6)
        }

        "when libType is not set should" >> inline {

          new StatsSeqGentrapOkTests(
            () => get(endpoint) { response }, 6)
        }

        "when libType is set to 'single' should" >> inline {

          new StatsSeqGentrapOkTests(() => get(endpoint, Seq(("libType", "single"))) { response }, 4)
        }

        "when libType is set to 'paired' should" >> inline {

          new StatsSeqGentrapOkTests(() => get(endpoint, Seq(("libType", "paired"))) { response }, 2)
        }

        "when qcPhase is set to 'processed' and sorted set to 'yes' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("qcPhase", "processed"), ("sorted", "yes"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            s"return a JSON list containing 6 objects" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(6)
            }

            "which should" >> {

              "have a different content compared to when qcPhase is set to 'raw' and sorted set to 'yes'" in {
                get(endpoint, Seq(("qcPhase", "raw"), ("sorted", "yes"))) {
                  jsonBody must not be equalTo(priorResponse.jsonBody)
                }
              }
            }
          }
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

        "when queried multiple times using the default parameter should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Stream.fill(10)(request)

            "return the items in random order" in {
              // if the items are returned in the same order, the 'body' string will be the same so the set size == 1
              priorResponses.map(_.body).distinct.size must beGreaterThan(1)
            }
          }
        }

        "when queried multiple times with sorted set to 'yes' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("sorted", "yes"))) { response }
            def priorRequests = Stream.fill(10)(request)

            "return the items in the nonrandom order" in {
              priorResponses.map(_.body).distinct.size mustEqual 1
            }
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/gentrap/sequences/aggregates'" >> {
    br

    val endpoint = s"$baseEndpoint/gentrap/sequences/aggregates"

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

    "when an invalid run ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("runIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid run ID(s) provided.")
        }
      }
    }

    "when an invalid reference ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("refIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid reference ID(s) provided.")
        }
      }
    }

    "when an invalid annotation ID is specified should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint, Seq(("annotIds", "yalala"))) { response }
        def priorRequests = Seq(request)

        "return status 400" in  {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid annotation ID(s) provided.")
        }
      }
    }

    "when the database is empty should" >> inline {

      new Context.PriorRequests {
        def request = () => get(endpoint) { response }
        def priorRequests = Seq(request)

        "return status 404" in  {
          priorResponse.status mustEqual 404
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "No data points for aggregation found.")
        }
      }
    }

    "using the gentrap v0.4 summary (3 samples, 6 library, mixed library types)" >> inline {

      new Context.PriorRunUploadClean {

        def pipelineParam = "gentrap"
        def uploadPayload = SchemaExamples.Gentrap.V04.MSampleMLibMixedLib

        "when using the default parameter should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              // read1
              priorResponse.body must /("read1") / "nBases"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesA"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesT"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesG"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesC"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesN"  /("count" -> 6)
              priorResponse.body must /("read1") / "nReads"  /("count" -> 6)
              // read2
              priorResponse.body must /("read2") / "nBases"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesA"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesT"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesG"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesC"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesN"  /("count" -> 2)
              priorResponse.body must /("read2") / "nReads"  /("count" -> 2)
            }
          }
        }

        "when libType is set to 'paired' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("libType", "paired"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              // read1
              priorResponse.body must /("read1") / "nBases"  /("count" -> 2)
              priorResponse.body must /("read1") / "nBasesA"  /("count" -> 2)
              priorResponse.body must /("read1") / "nBasesT"  /("count" -> 2)
              priorResponse.body must /("read1") / "nBasesG"  /("count" -> 2)
              priorResponse.body must /("read1") / "nBasesC"  /("count" -> 2)
              priorResponse.body must /("read1") / "nBasesN"  /("count" -> 2)
              priorResponse.body must /("read1") / "nReads"  /("count" -> 2)
              // read2
              priorResponse.body must /("read2") / "nBases"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesA"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesT"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesG"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesC"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesN"  /("count" -> 2)
              priorResponse.body must /("read2") / "nReads"  /("count" -> 2)
            }
          }
        }

        "when libType is set to 'single' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("libType", "single"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              // read1
              priorResponse.body must /("read1") / "nBases"  /("count" -> 4)
              priorResponse.body must /("read1") / "nBasesA"  /("count" -> 4)
              priorResponse.body must /("read1") / "nBasesT"  /("count" -> 4)
              priorResponse.body must /("read1") / "nBasesG"  /("count" -> 4)
              priorResponse.body must /("read1") / "nBasesC"  /("count" -> 4)
              priorResponse.body must /("read1") / "nBasesN"  /("count" -> 4)
              priorResponse.body must /("read1") / "nReads"  /("count" -> 4)
              // read2
              priorResponse.body must not / "read2"
            }
          }
        }

        "when qcPhase is set to 'processed' should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint, Seq(("qcPhase", "raw"))) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              // read1
              priorResponse.body must /("read1") / "nBases"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesA"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesT"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesG"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesC"  /("count" -> 6)
              priorResponse.body must /("read1") / "nBasesN"  /("count" -> 6)
              priorResponse.body must /("read1") / "nReads"  /("count" -> 6)
              // read2
              priorResponse.body must /("read2") / "nBases"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesA"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesT"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesG"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesC"  /("count" -> 2)
              priorResponse.body must /("read2") / "nBasesN"  /("count" -> 2)
              priorResponse.body must /("read2") / "nReads"  /("count" -> 2)
            }

            "return a different object than when qcPhase is set to 'processed'" in {
              get(endpoint, Seq(("qcPhase", "processed"))) {
                response.body must not be equalTo(priorResponse.body)
              }
            }
          }
        }
      }
    }
  }
}
