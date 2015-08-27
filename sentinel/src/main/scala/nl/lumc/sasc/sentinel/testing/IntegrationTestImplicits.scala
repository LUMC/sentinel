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
package nl.lumc.sasc.sentinel.testing

import scala.util.Try

import org.scalatra.test.specs2.BaseScalatraSpec
import org.specs2.matcher.ThrownExpectations

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.ClientResponse
import org.specs2.data.Sized

trait IntegrationTestImplicits { this: BaseScalatraSpec with ThrownExpectations =>

  /** Implicit class for testing convenience. */
  implicit class RichClientResponse(httpres: ClientResponse) {

    /** Response body represented as JSON, if possible. */
    lazy val jsonBody: Option[JValue] = Try(parse(httpres.body)).toOption

    /** Response content type. */
    lazy val contentType = httpres.mediaType.getOrElse(failure("'Content-Type' not found in response header."))
  }

  // TODO: Use the specs2 built-in raw JSON matcher when we switch to specs2-3.6
  /** Specs2 matcher for testing size of JSON response bodies. */
  implicit def jsonBodyIsSized: Sized[Option[JValue]] = new Sized[Option[JValue]] {
    def size(t: Option[JValue]) = t match {
      case None => -1
      case Some(jvalue) => jvalue match {
        case JArray(list)     => list.size
        case JObject(objects) => objects.size
        case JString(str)     => str.length
        case otherwise        => -1
      }
    }
  }

  /** Specs2 matcher for size of JSON objects. */
  implicit def jsonIsSized: Sized[JValue] = new Sized[JValue] {
    def size(t: JValue) = t match {
      case JArray(list)     => list.size
      case JObject(objects) => objects.size
      case JString(str)     => str.length
      case otherwise        => -1
    }
  }
}

