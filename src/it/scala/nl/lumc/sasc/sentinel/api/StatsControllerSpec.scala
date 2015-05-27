package nl.lumc.sasc.sentinel.api

import java.io.File

import org.scalatra.test.ClientResponse

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.processors.gentrap.GentrapAlignmentStats
import nl.lumc.sasc.sentinel.utils.getResourceFile

class StatsControllerSpec extends SentinelServletSpec {

  sequential

  trait UploadThenStatsContext extends SpecContext.AfterRequest[ClientResponse] {
    def pipeline: String
    def runFile: File
    def uploadEndpoint = "/runs"
    def uploadParams = Seq(("userId", user.id), ("pipeline", pipeline))
    def uploadFile = Map("run" -> runFile)
    def uploadHeader = Map(HeaderApiKey -> user.activeKey)
    def requestMethod = post(uploadEndpoint, uploadParams, uploadFile, uploadHeader) { response }

    "after the summary file is uploaded to an empty database" in {
      requestResponse.statusLine.code mustEqual 201
    }
  }

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dbAccess
  val statsServlet = new StatsController
  val runsServlet = new RunsController
  addServlet(statsServlet, "/stats/*")
  addServlet(runsServlet, "/runs/*")

  "GET '/stats/alignments/gentrap'" >> {
    br

    val endpoint = "/stats/alignments/gentrap"

    class GentrapV04MultiSampleSingleLibContext extends UploadThenStatsContext {
      def pipeline = "gentrap"
      lazy val runFile = getResourceFile("/schema_examples/biopet/v0.4/gentrap_multi_sample_single_lib.json")
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
              jsonBody must beSome
              val contents = jsonBody.get.extract[List[GentrapAlignmentStats]]
              contents must haveSize(2)
            }
          }
        }
      }
    }
  }
}
