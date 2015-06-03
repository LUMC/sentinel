package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.SentinelServletSpec

class StatsControllerSpec extends SentinelServletSpec {

  import Context.{ PriorRequests => PriorRequestsContext, PriorRunUploadClean => PriorRunUploadCleanContext }

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

  s"GET '$baseEndpoint/alignments/gentrap'" >> {
    br

    val endpoint = s"$baseEndpoint/alignments/gentrap"

    "when an invalid accumulation level is specified should" >> inline {

      new PriorRequestsContext {
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

      new PriorRequestsContext {
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

      new PriorRunUploadCleanContext {

        def pipelineParam = "gentrap"
        lazy val uploadPayload = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")

        "when using the default parameter should" >> inline {

          new PriorRequestsContext {

            def request = () => get(endpoint) { response }
            override def priorRequests: Seq[Req] = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON list containing 2 objects" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(2)
            }

            "each of which" should {
              Seq(0, 1) foreach { idx =>
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
        }
      }
    }
  }

  s"GET '$baseEndpoint/sequences/gentrap'" >> {
    br

    val endpoint = s"$baseEndpoint/sequences/gentrap"

    "when an invalid library type is specified should" >> inline {

      new PriorRequestsContext {
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

      new PriorRequestsContext {
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

      new PriorRunUploadCleanContext {

        def pipelineParam = "gentrap"
        lazy val uploadPayload = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")

        "when using the default parameter should" >> inline {

          new PriorRequestsContext {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON list containing 2 objects" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(2)
            }

            "each of which" should {
              Seq(0, 1) foreach { idx =>
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
        }

      }
    }
  }
}
