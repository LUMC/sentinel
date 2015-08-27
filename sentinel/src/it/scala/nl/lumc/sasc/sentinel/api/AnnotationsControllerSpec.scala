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

import nl.lumc.sasc.sentinel.exts.pann.PannRunsProcessor
import nl.lumc.sasc.sentinel.exts.plain.PlainRunsProcessor
import nl.lumc.sasc.sentinel.testing.SentinelServletSpec
import nl.lumc.sasc.sentinel.utils.reflect.makeDelayedProcessor

class AnnotationsControllerSpec extends SentinelServletSpec {

  implicit val mongo = dao
  implicit val runsProcessorMakers = Set(
    makeDelayedProcessor[PannRunsProcessor],
    makeDelayedProcessor[PlainRunsProcessor])
  val baseEndpoint = "/annotations"
  val annotsServlet = new AnnotationsController
  val runsServlet = new RunsController
  addServlet(annotsServlet, s"$baseEndpoint/*")
  addServlet(runsServlet, "/runs/*")

  s"OPTIONS '$baseEndpoint'" >> {
    br
    "when using the default parameter should" >> inline {
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

    "using a summary file that contain annotation entries" >> inline {

      new Context.PriorRunUploadClean {

        def uploadSet = UploadSet(users.avg, SummaryExamples.Pann.Ann1, "pann")
        def priorRequests = Seq(uploadSet.request)

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

            "return a JSON list containing 2 objects" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.jsonBody must haveSize(2)
            }

            "each of which" should {
              Range(0 ,2) foreach { idx =>
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

    "using multiple summary files that contain overlapping annotation entries should" >> inline {

      new Context.PriorRunUploadClean {

        def uploadSet1 = UploadSet(users.avg, SummaryExamples.Pann.Ann1, "pann")
        def uploadSet2 = UploadSet(users.admin, SummaryExamples.Pann.Ann2, "pann")
        def priorRequests = Seq(uploadSet1, uploadSet2).map(_.request)

        "after the first file is uploaded" in {
          priorResponses.head.status mustEqual 201
        }

        "after the second file is uploaded" in {
          priorResponses(1).status mustEqual 201
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

  s"OPTIONS '$baseEndpoint/:annotId'" >> {
    br
    "when using the default parameter should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint/annotId", "GET,HEAD")
    }
  }

  s"GET '$baseEndpoint/:annotId'" >> {
  br

    def endpoint(annotId: String) = s"$baseEndpoint/$annotId"

    "using a run summary file that contain annotation entries" >> inline {

      new Context.PriorRunUploadClean {

        def uploadSet = UploadSet(users.avg, SummaryExamples.Pann.Ann1, "pann")
        def priorRequests = Seq(uploadSet.request)
        def annotIds = (parse(priorResponse.body) \ "annotIds").extract[Seq[String]]
        def runId = (parse(priorResponse.body) \ "runId").extract[String]

        "after the run summary file is uploaded" in {
          priorResponse.status mustEqual 201
        }

        "when an annotation entry with an invalid ID is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint("yalala")) { response }
            def priorRequests = Seq(request)

            "return status 404" in {
              priorResponse.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Annotation ID can not be found.")
            }
          }
        }

        "when a nonexistent annotation entry is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint(runId)) { response }
            def priorRequests = Seq(request)

            "return status 404" in {
              priorResponse.status mustEqual 404
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Annotation ID can not be found.")
            }
          }
        }

        "when an existing annotation entry is queried should" >> inline {

          new Context.PriorRequests {

            def request = () => get(endpoint(annotIds.head)) { response }
            def priorRequests = Seq(request)

            "return status 200" in {
              priorResponse.status mustEqual 200
            }

            "return a JSON object containing the expected attributes" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("annotId" -> """\S+""".r)
              priorResponse.body must /("annotMd5" -> """\S+""".r)
            }
          }
        }
      }
    }
  }
}
