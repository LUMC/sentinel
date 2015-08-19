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
package nl.lumc.sasc.sentinel

import scala.util.Try

import org.scalatra.test.specs2.BaseScalatraSpec
import org.specs2.matcher.ThrownExpectations

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.ClientResponse
import org.specs2.data.Sized

import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.api.SentinelServletSpec.makeUploadable

package object api {

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
          case JArray(list) => list.size
          case JObject(objects) => objects.size
          case JString(str) => str.length
          case otherwise => -1
        }
      }
    }

    /** Specs2 matcher for size of JSON objects. */
    implicit def jsonIsSized: Sized[JValue] =  new Sized[JValue] {
      def size(t: JValue) = t match {
        case JArray(list) => list.size
        case JObject(objects) => objects.size
        case JString(str) => str.length
        case otherwise => -1
      }
    }
  }

  /** Various types of User objects for testing. */
  object UserExamples {

    import User.hashPassword

    /** Expected normal user: verified but not an admin. */
    val avg = User("avg", "avg@test.id", hashPassword("0PwdAvg"), "key1", verified = true, isAdmin = false)

    /** Also an expected normal user: verified but not an admin. */
    val avg2 = User("avg2", "avg2@test.id", hashPassword("0PwdAvg2"), "key2", verified = true, isAdmin = false)

    /** Admin user. */
    val admin = User("admin", "admin@test.id", hashPassword("0PwdAdmin"), "key3", verified = true, isAdmin = true)

    /** Unverified user. */
    val unverified = User("unv", "unv@test.id", hashPassword("0PwdUnverified"), "key4", verified = false, isAdmin = false)

    /** Set of all testing users. */
    def all = Set(avg, avg2, admin, unverified)
  }

  /** Convenience container for uploadable run summaries. */
  object SummaryExamples {

    /** Plain summary file. */
    lazy val Plain = makeUploadable("/summary_examples/plain.json")

    /** Plain summary file, compressed. */
    lazy val PlainCompressed = makeUploadable("/summary_examples/plain.json.gz")

    /** Summary file that is JSON but invalid. */
    lazy val Invalid = makeUploadable("/summary_examples/invalid.json")

    /** Non-JSON file. */
    lazy val Not = makeUploadable("/summary_examples/not.json")

    object Maple {

      /** Single sample, single read group. */
      lazy val SSampleSRG = makeUploadable("/summary_examples/maple/maple_single_sample_single_rg.json")

      /** Single sample, multiple read groups. */
      lazy val SSampleMRG = makeUploadable("/summary_examples/maple/maple_single_sample_multi_rg.json")

      /** Multiple samples, single read group each. */
      lazy val MSampleSRG = makeUploadable("/summary_examples/maple/maple_multi_sample_single_rg.json")

      /** Multiple samples, multiple read groups. */
      lazy val MSampleMRG = makeUploadable("/summary_examples/maple/maple_multi_sample_multi_rg.json")

    }

    object Pref {

      /** Contains a reference. */
      lazy val Ref1 = makeUploadable("/summary_examples/pref/pref_01.json")

      /** Contains another reference. */
      lazy val Ref2 = makeUploadable("/summary_examples/pref/pref_02.json")

      /** Contains the same reference as Ref2. */
      lazy val Ref3 = makeUploadable("/summary_examples/pref/pref_03.json")
    }

    object Pann {

      /** Contains two annotations. */
      lazy val Ann1 = makeUploadable("/summary_examples/pann/pann_01.json")

      /** Contains one annotation already in Ann1. */
      lazy val Ann2 = makeUploadable("/summary_examples/pann/pann_02.json")
    }
  }
}

