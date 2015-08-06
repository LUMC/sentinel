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
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.Uploadable

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.reflect.runsProcessorMaker

class ReferencesControllerSpec extends SentinelServletSpec {

  import SentinelServletSpec.SchemaExamples

  sequential

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04RunsProcessor],
    runsProcessorMaker[nl.lumc.sasc.sentinel.processors.plain.PlainRunsProcessor])
  val baseEndpoint = "/references"
  val refsServlet = new ReferencesController
  val runsServlet = new RunsController
  addServlet(refsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"OPTIONS '$baseEndpoint'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint", "GET,HEAD")
    }
  }

  s"GET '$baseEndpoint'" >> {
    br

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

    "using a summary file that contain a reference entry" >> inline {

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"
        def params = Seq(("userId", Users.avg.id), ("pipeline", "gentrap"))
        def headers = Map(HeaderApiKey -> Users.avg.activeKey)
        def request = () => post(uploadEndpoint, params,
          Map("run" -> SchemaExamples.Gentrap.V04.SSampleSLib), headers) { response}
        def priorRequests = Seq(request)

        "after the run summary file is uploaded" in {
          priorResponses.head.status mustEqual 201
        }

        "when using the default parameters should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON list containing 1 object" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(1)
            }

            "which" should {
              s"have the expected attributes" in {
                priorResponse.body must /#(0) /("refId" -> """\S+""".r)
                priorResponse.body must /#(0) /("combinedMd5" -> """\S+""".r)
                priorResponse.jsonBody must beSome.like { case json =>
                  (json(0) \ "contigs" \\ "md5").children
                    .map(_.extract[String]).size must beGreaterThan(0)
                }
              }
            }
          }
        }
      }
    }

    "using multiple summary files that contain overlapping reference entries should" >> inline {

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
        def upload4 = makeUpload(Users.avg, SchemaExamples.Plain)

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

            "return a JSON list containing 1 object" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(1)
            }

            "which should" should {
              s"have the expected attributes" in {
                priorResponse.body must /#(0) /("refId" -> """\S+""".r)
                priorResponse.body must /#(0) /("combinedMd5" -> """\S+""".r)
                priorResponse.jsonBody must beSome.like { case json =>
                  (json(0) \ "contigs" \\ "md5").children
                    .map(_.extract[String]).size must beGreaterThan(0)
                }
              }
            }
          }
        }
      }
    }
  }

  s"OPTIONS '$baseEndpoint/:refId'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint/refId", "GET,HEAD")
    }
  }

  s"GET '$baseEndpoint/:refId'" >> {
  br

    def endpoint(refId: String) = s"$baseEndpoint/$refId"

    "using a run summary file that contains a reference entry" >> inline {

      new Context.PriorRequestsClean {

        def uploadEndpoint = "/runs"
        def params = Seq(("userId", Users.avg.id), ("pipeline", "gentrap"))
        def headers = Map(HeaderApiKey -> Users.avg.activeKey)
        def upload = () => post(uploadEndpoint, params,
          Map("run" -> SchemaExamples.Gentrap.V04.SSampleSLib), headers) { response}
        def priorRequests = Seq(upload)
        def refId = (parse(priorResponse.body) \ "refId").extract[String]
        def runId = (parse(priorResponse.body) \ "runId").extract[String]

        "after the run summary file is uploaded" in {
          priorResponse.status mustEqual 201
        }

        "when a reference entry with invalid ID is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint("yalala")) { response }
            def priorRequests = Seq(request)

            "return status 404" in {
              priorResponse.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Reference ID can not be found.")
            }
          }
        }

        "when a nonexistent reference entry is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint(runId)) { response }
            def priorRequests = Seq(request)

            "return status 404" in {
              priorResponse.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Reference ID can not be found.")
            }
          }
        }

        "when an existing reference entry is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint(refId)) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("refId" -> """\S+""".r)
              priorResponse.body must /("combinedMd5" -> """\S+""".r)
              priorResponse.jsonBody must beSome.like { case json =>
                (json \ "contigs" \\ "md5").children
                  .map(_.extract[String]).size must beGreaterThan(0)
              }
            }
          }
        }
      }
    }
  }
}
