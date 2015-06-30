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

import akka.actor.ActorSystem
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.CurrentApiVersion

class RootControllerSpec extends SentinelServletSpec with Mockito {

  implicit val system = mock[ActorSystem]
  implicit val swagger = new SentinelSwagger
  addServlet(new RootController, s"/*")
  addServlet(new ResourcesApp, "/api-spec/*")

  "OPTIONS '/'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest("/", "GET,HEAD")
    }
  }

  "GET '/' should" >> inline {

    val endpoint = "/"

    new Context.PriorRequests {

      def request = () => get(endpoint) { response }
      def priorRequests = Seq(request)

      "return status 301" in {
        priorResponse.status mustEqual 301
      }

      "redirect to /api-spec" in {
        priorResponse.header("Location") must endWith("/api-spec")
      }

      "return an empty body" in {
        priorResponse.body must beEmpty
      }
    }
  }

  "GET '/api-spec' should" >> inline {

    val endpoint = "/api-spec"

    new Context.PriorRequests {

      def request = () => get(endpoint) { response }
      def priorRequests = Seq(request)

      "return status 200" in {
        priorResponse.status mustEqual 200
      }

      "return a JSON object containing the API version" in {
        priorResponse.contentType mustEqual "application/json"
        priorResponse.body must /("apiVersion" -> CurrentApiVersion)
      }
    }
  }
}
