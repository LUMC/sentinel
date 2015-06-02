package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.SentinelServletSpec

class StatsControllerSpec extends SentinelServletSpec {

  sequential

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val statsServlet = new StatsController
  val runsServlet = new RunsController
  addServlet(statsServlet, "/stats/*")
  addServlet(runsServlet, "/runs/*")

  "GET '/stats/alignments/gentrap'" >> {
    br

    val endpoint = "/stats/alignments/gentrap"

    class GentrapV04MultiSampleSingleLibContext extends SpecContext.PriorRunUpload {
      def pipelineParam = "gentrap"
      lazy val uploadPayload = makeUploadable("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")
    }

    "using gentrap v0.4 summary containing 2 samples with 2 libraries total" >> inline {

      new GentrapV04MultiSampleSingleLibContext {

        "when using the default parameters should" >> {

          "return status 200" in {
            get(endpoint) {
              status mustEqual 200
            }
          }

          "return a JSON list with 2 objects" >> {
            get(endpoint) {
              jsonBody must haveSize(2)
            }
          }
        }
      }
    }
  }
}
