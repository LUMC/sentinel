package nl.lumc.sasc.sentinel.api

import org.scalatra.test.Uploadable

import nl.lumc.sasc.sentinel.{ HeaderApiKey, SentinelServletSpec }
import nl.lumc.sasc.sentinel.SentinelServletSpec.SchemaExamples
import nl.lumc.sasc.sentinel.models.User

class AnnotationsControllerSpec extends SentinelServletSpec {

  sequential

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val baseEndpoint = "/annotations"
  val annotsServlet = new AnnotationsController
  val runsServlet = new RunsController
  addServlet(annotsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"GET '$baseEndpoint'" >> {

    val endpoint = baseEndpoint

    "when the database is empty should" >> inline {

      new Context.PriorRequests {

        def request = () => get(endpoint) { response }
        def priorRequests = Seq(request)

        "return status 200" in {
          priorResponse.status mustEqual 200
        }

        "return an empty JSON list" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.jsonBody must haveSize(0)
        }
      }
    }

    "using summary files that contain annotation entries should" >> inline {

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"
        def pipeline = "gentrap"

        def makeUpload(uploader: User, uploaded: Uploadable): Req = {
          val params = Seq(("userId", uploader.id), ("pipeline", pipeline))
          val headers = Map(HeaderApiKey -> uploader.activeKey)
          () => post(uploadEndpoint, params, Map("run" -> uploaded), headers) { response }
        }

        def upload1 = makeUpload(Users.admin, SchemaExamples.Gentrap.V04.SSampleMLib)
        def upload2 = makeUpload(Users.avg2, SchemaExamples.Gentrap.V04.MSampleMLib)
        def upload3 = makeUpload(Users.avg2, SchemaExamples.Gentrap.V04.MSampleSLib)
        def upload4 = makeUpload(Users.avg, SchemaExamples.Unsupported)

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

        "when using the default parameters should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON list containing 3 objects" in { // we have 3 unique annotations in all the uploaded files
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(3)
            }

            "each of which" should {
              Range(0 ,3) foreach { idx =>
                val item = idx + 1
                s"have the expected attributes (object #$item)" in {
                  priorResponse.body must /#(idx) /("annotId" -> """\S+""".r)
                  priorResponse.body must /#(idx) /("annotMd5" -> """\S+""".r)
                }
              }
            }
          }
        }
      }
    }
  }
}
