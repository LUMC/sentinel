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

import scala.concurrent.Await
import scala.concurrent.duration._

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.testing.{ SentinelServletSpec, UserExamples }
import nl.lumc.sasc.sentinel.models.{SinglePathPatch, UserRequest}

class UsersControllerSpec extends SentinelServletSpec {

  private def makeBasicAuthHeader(userId: String, password: String): String =
    "Basic " + BaseEncoding.base64().encode(s"$userId:$password".getBytes(Charsets.UTF_8))
  implicit val mongo = dao
  val servlet = new UsersController
  val baseEndpoint = "/users"
  addServlet(servlet, s"$baseEndpoint/*")

  s"OPTIONS '$baseEndpoint'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint", "HEAD,POST")
    }
  }

  s"POST '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the user is successfully created should" >> inline {

      new Context.PriorRequestsClean {

        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toJsonByteArray(userRequest)
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 201" in {
          priorResponse.status mustEqual 201
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "New user created.")
          priorResponse.body must /("hints") /# 0 / s"uri: /users/${userRequest.id}"
          priorResponse.body must /("hints") /# 1 / """apiKey: \S+""".r
        }
      }
    }

    "when the request body is empty should" >> inline {

      new Context.PriorRequestsClean {

        def request = () => post(endpoint, body = Array.empty[Byte]) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Malformed user request.")
        }
      }
    }

    "when the request body is not valid JSON should" >> inline {

      new Context.PriorRequestsClean {

        def request = () => post(endpoint, body = Array[Byte](10, 20, 30)) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Malformed user request.")
        }
      }
    }

    "when the passwords do not match should" >> inline {

      new Context.PriorRequestsClean {

        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "Mypass456"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") / "Different passwords given."
        }
      }
    }

    "when the user ID is less than 3 characters should" >> inline {

      new Context.PriorRequestsClean {

        val payload = toJsonByteArray(UserRequest("hm", "mail@mail.com", "MyPass123", "Mypass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") / "User ID shorter than 3 characters."
        }
      }
    }

    "when the user ID contains disallowed characters which" can {

      Seq(" ", ".", "*23#%") foreach { nonchar =>

        s"be '$nonchar' should" >> inline {
          
          new Context.PriorRequestsClean {
            val payload = toJsonByteArray(UserRequest("yeah" + nonchar, "mail@mail.com", "Mypass123", "Mypass123"))
            def request = () => post(endpoint, payload) { response }
            def priorRequests = Seq(request)

            "return status 400" in {
              priorResponse.status mustEqual 400
            }

            "return a JSON object containing the expected message" in {
              priorResponse.body must /("message" -> "Invalid user request.")
              priorResponse.body must /("hints") / "User ID contains disallowed characters: .+".r
            }
          }
        }
      }
    }

    "when the password is less than 6 characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "My1aB", "My1aB"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") / "Password shorter than 6 characters."
        }
      }
    }

    "when the password does not contain any uppercase characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain any lowercase characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain any numeric characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "MyPass", "MyPass"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("hints") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the requested user ID already exists should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toJsonByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "MyPass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request, request)

        "return status 409" in {
          priorResponses.last.status mustEqual 409
        }

        "return a JSON object containing the expected message" in {
          priorResponses.last.body must /("message" -> "User ID already taken.")
          priorResponses.last.body must /("hints") /# 0 / startWith("Existing ID: yeah.")
        }
      }
    }
  }

  s"OPTIONS '$baseEndpoint/:userRecordId'" >> {
    br
    "when using the default parameters should" >> inline {
      new Context.OptionsMethodTest(s"$baseEndpoint/userRecordId", "GET,HEAD,PATCH")
    }
  }

  s"GET '$baseEndpoint/:userRecordId'" >> {
    br

    def endpoint(userRecordId: String) = s"$baseEndpoint/$userRecordId"

    "when the user record ID is not specified should" >> inline {

      new Context.PriorRequests {

        def request = () => get(endpoint("")) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "User record ID not specified.")
        }
      }
    }

    "when the user ID is not specified should" >> inline {

      new Context.PriorRequests {

        def request = () => get(endpoint(UserExamples.avg.id)) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "User ID not specified.")
        }
      }
    }

    s"when the user does not provide the $HeaderApiKey header should" >> inline {

      new Context.PriorRequestsClean {

        def request = () => get(endpoint(UserExamples.avg.id), Seq(("userId", UserExamples.avg.id))) { response }
        def priorRequests = Seq(request)

        "return status 401" in {
          priorResponse.status mustEqual 401
        }

        "return the challenge response header key" in {
          priorResponse.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Authentication required to access resource.")
        }
      }
    }

    s"when the provided $HeaderApiKey does not match the one owned by the user should" >> inline {

      new Context.PriorRequestsClean {

        def params = Seq(("userId", UserExamples.avg.id))
        def headers = Map(HeaderApiKey -> (user.activeKey + "nono"))
        def request = () => get(endpoint(UserExamples.avg.id), params, headers) { response }
        def priorRequests = Seq(request)

        "return status 401" in {
          priorResponse.status mustEqual 401
        }

        "return the challenge response header key" in {
          priorResponse.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Authentication required to access resource.")
        }
      }
    }

    s"when an unverified user requests a user record" >> inline {

      new Context.PriorRequestsClean {

        def params = Seq(("userId", UserExamples.unverified.id))
        def headers = Map(HeaderApiKey -> UserExamples.unverified.activeKey)
        def request = () => get(endpoint(UserExamples.unverified.id), params, headers) { response }
        def priorRequests = Seq(request)

        "return status 403" in {
          priorResponse.status mustEqual 403
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Unauthorized to access resource.")
        }
      }
    }

    "when the user authenticates correctly" >> {
      br

      "and the user requests his/her own record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", UserExamples.avg.id))
          def headers = Map(HeaderApiKey -> UserExamples.avg.activeKey)
          def request = () => get(endpoint(UserExamples.avg.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> UserExamples.avg.id)
            priorResponse.body must /("email" -> UserExamples.avg.email)
            priorResponse.body must /("activeKey" -> UserExamples.avg.activeKey)
          }
        }
      }

      "and the user is an admin that requests another user's record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", UserExamples.admin.id))
          def headers = Map(HeaderApiKey -> UserExamples.admin.activeKey)
          def request = () => get(endpoint(UserExamples.avg.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> UserExamples.avg.id)
            priorResponse.body must /("email" -> UserExamples.avg.email)
            priorResponse.body must /("activeKey" -> UserExamples.avg.activeKey)
          }
        }
      }

      "and the user is an admin that requests his/her own record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", UserExamples.admin.id))
          def headers = Map(HeaderApiKey -> UserExamples.admin.activeKey)
          def request = () => get(endpoint(UserExamples.admin.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> UserExamples.admin.id)
            priorResponse.body must /("email" -> UserExamples.admin.email)
            priorResponse.body must /("activeKey" -> UserExamples.admin.activeKey)
          }
        }
      }

      "and the user is not an admin and requests someone else's record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", UserExamples.avg.id))
          def headers = Map(HeaderApiKey -> UserExamples.avg.activeKey)
          def request = () => get(endpoint(UserExamples.avg2.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 403" in {
            priorResponse.status mustEqual 403
          }

          "return a JSON object containing the expected message" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("message" -> "Unauthorized to access resource.")
          }
        }
      }
    }
  }

  s"PATCH '$baseEndpoint/:userRecordId'" >> {
    br

    def endpoint(userRecordId: String) = s"$baseEndpoint/$userRecordId"

    "when the user record ID is not specified should" >> inline {

      new Context.PriorRequests {

        def request = () => patch(endpoint("")) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "User record ID not specified.")
        }
      }
    }

    "when the user ID is not specified should" >> inline {

      new Context.PriorRequestsClean {

        def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/password", "newPass123")))
        def request = () => patch(endpoint(UserExamples.avg.id), payload) { response }
        def priorRequests = Seq(request)

        "return status 401" in {
          priorResponse.status mustEqual 401
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Authentication required to access resource.")
        }
      }
    }

    "using basic HTTP authentication" >> {
      br

      "when done by a valid user which" can {

        val userSets = Seq(
          ("an admin user", "0PwdAdmin", UserExamples.admin),
          ("a non-admin user to his/her own account", "0PwdAvg", UserExamples.avg))

        userSets foreach { case (utype, pwd, uobj) =>

          s"be $utype" should {

            def headers = Map("Authorization" -> makeBasicAuthHeader(uobj.id, pwd))

            "when the password does not match should" >> inline {

              def headers = Map("Authorization" -> makeBasicAuthHeader(uobj.id, s"${pwd}_nomatch"))

              new Context.PriorRequestsClean {

                def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/verified", true)))
                def request = () => patch(endpoint(UserExamples.unverified.id), payload, headers) { response }
                def priorRequests = Seq(request)

                "return status 401" in {
                  priorResponse.status mustEqual 401
                }

                "return the challenge response header key" in {
                  priorResponse.header must havePair("WWW-Authenticate" -> "Basic realm=\"Sentinel Admins\"")
                }

                "return a JSON object containing the expected message" in {
                  priorResponse.contentType mustEqual "application/json"
                  priorResponse.body must /("message" -> "Authentication required to access resource.")
                }

                "not change the user authentication status" in {
                  // TODO: find a way to use matcher without using Await explicitly
                  val results = Await.result(servlet.users.getUser(UserExamples.unverified.id), 1000.milli)
                  results must beSome.like { case user => user.verified must beFalse }
                }
              }
            }

            "when he/she authenticates correctly" >> {
              br

              "when the patch document is non-JSON should" >> inline {

                new Context.PriorRequestsClean {

                  def request = () => patch(endpoint(uobj.id), Array[Byte](10, 20 ,30), headers) { response }
                  def priorRequests = Seq(request)

                  "return status 400" in {
                    priorResponse.status mustEqual 400
                  }

                  "return a JSON object containing the expected message" in {
                    priorResponse.contentType mustEqual "application/json"
                    priorResponse.body must /("message" -> "JSON is invalid.")
                    priorResponse.body must /("hints") /# 0 / "Invalid syntax."
                  }
                }
              }

              "when the patch document is empty should" >> inline {

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq())
                  def request = () => patch(endpoint(uobj.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return status 400" in {
                    priorResponse.status mustEqual 400
                  }

                  "return a JSON object containing the expected message" in {
                    priorResponse.contentType mustEqual "application/json"
                    priorResponse.body must /("message" -> "JSON is invalid.")
                    priorResponse.body must /("hints") /# 0 / startWith("Nothing to parse.")
                  }
                }
              }

              "when the patch document contains an invalid entry should" >> inline {

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq("yalala", SinglePathPatch("replace", "/password", "newPass123")))
                  def request = () => patch(endpoint(uobj.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return status 400" in {
                    priorResponse.status mustEqual 400
                  }

                  "return a JSON object containing the expected message" in {
                    priorResponse.contentType mustEqual "application/json"
                    priorResponse.body must /("message" -> "JSON is invalid.")
                    priorResponse.body must /("hints") /# 0 / startWith("error: instance failed to match")
                  }
                }
              }

              "when the patch document contains disallowed 'op' values which" can {

                Seq("add", "remove", "test") foreach { case op =>

                  s"be '$op' should" >> inline {

                    new Context.PriorRequestsClean {

                      def payload = toJsonByteArray(
                        Seq(SinglePathPatch("replace", "/password", "newPass123"), SinglePathPatch(op, "/password", "newPass123")))
                      def request = () => patch(endpoint(uobj.id), payload, headers) { response }
                      def priorRequests = Seq(request)

                      "return status 400" in {
                        priorResponse.status mustEqual 400
                      }

                      "return a JSON object containing the expected message" in {
                        priorResponse.contentType mustEqual "application/json"
                        priorResponse.body must /("message" -> "Invalid patch operation(s).")
                        priorResponse.body must /("hints") /# 0 / s"Unsupported operation: '$op'."
                      }
                    }
                  }
                }
              }

              "when the patch contains an op with path '/verified' and targets another user" >> inline {

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/verified", true)))
                  def request = () => patch(endpoint(UserExamples.unverified.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return the expected status code" in {
                    if (uobj == UserExamples.admin) priorResponse.status mustEqual 204
                    else priorResponse.status mustEqual 403

                  }

                  "return the expected body" in {
                    priorResponse.contentType mustEqual "application/json"
                    if (uobj == UserExamples.admin) priorResponse.body must beEmpty
                    else priorResponse.body must /("message" -> "Unauthorized to access resource.")
                  }

                  "change the user verification status if admin and not if not admin" in {
                    // TODO: find a way to use matcher without using Await explicitly
                    val results = Await.result(servlet.users.getUser(UserExamples.unverified.id), 1000.milli)
                    results must beSome.like { case user =>
                      if (uobj == UserExamples.admin) user.verified must beTrue
                      else user.verified must beFalse
                    }
                  }
                }
              }

              "when the patch contains an op with path '/verified' and targets the same user" >> inline {

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/verified", false)))
                  def request = () => patch(endpoint(uobj.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return the expected status code" in {
                    if (uobj == UserExamples.admin) priorResponse.status mustEqual 204
                    else priorResponse.status mustEqual 403

                  }

                  "return the expected body" in {
                    priorResponse.contentType mustEqual "application/json"
                    if (uobj == UserExamples.admin) priorResponse.body must beEmpty
                    else priorResponse.body must /("message" -> "Unauthorized to access resource.")
                  }

                  "change the user verification status if admin and not if not admin" in {
                    // TODO: find a way to use matcher without using Await explicitly
                    val results = Await.result(servlet.users.getUser(uobj.id), 1000.milli)
                    results must beSome.like { case user =>
                      if (uobj == UserExamples.admin) user.verified must beFalse
                      else user.verified must beTrue
                    }
                  }
                }
              }

              "when the patch contains an op with path '/password' should" >> inline {

                val newPass = "newPass123"

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/password", newPass)))
                  def request = () => patch(endpoint(UserExamples.avg.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return status 204" in {
                    priorResponse.status mustEqual 204
                  }

                  "return an empty body" in {
                    priorResponse.contentType mustEqual "application/json"
                    priorResponse.body must beEmpty
                  }

                  "change the user password" in {
                    // TODO: find a way to use matcher without using Await explicitly
                    val results = servlet.users.getUser(UserExamples.avg.id)
                    Await.result(results, 1000.milli) must beSome.like { case user =>
                      user.passwordMatches(newPass) must beTrue
                      user.passwordMatches("0PwdAvg") must beFalse
                    }
                  }
                }
              }

              "when the patch contains an op with path '/email' should" >> inline {

                val newEmail = "new@email.com"

                new Context.PriorRequestsClean {

                  def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/email", newEmail)))
                  def request = () => patch(endpoint(UserExamples.avg.id), payload, headers) { response }
                  def priorRequests = Seq(request)

                  "return status 204" in {
                    priorResponse.status mustEqual 204
                  }

                  "return an empty body" in {
                    priorResponse.contentType mustEqual "application/json"
                    priorResponse.body must beEmpty
                  }

                  "change the user email" in {
                    // TODO: find a way to use matcher without using Await explicitly
                    val results = Await.result(servlet.users.getUser(UserExamples.avg.id), 1000.milli)
                    results must beSome.like { case user => user.email mustEqual newEmail }
                  }
                }
              }
            }
          }
        }
      }

      "when done by an unverified user" >> {
        br

        def password = "0PwdUnverified"
        def userRecord = UserExamples.unverified
        def headers = Map("Authorization" -> makeBasicAuthHeader(userRecord.id, password))

        "when the password does not match should" >> inline {

          def headers = Map("Authorization" -> makeBasicAuthHeader(userRecord.id, password + "_nomatch"))

          new Context.PriorRequestsClean {

            def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/verified", true)))
            def request = () => patch(endpoint(UserExamples.unverified.id), payload, headers) { response }
            def priorRequests = Seq(request)

            "return status 401" in {
              priorResponse.status mustEqual 401
            }

            "return the challenge response header key" in {
              priorResponse.header must havePair("WWW-Authenticate" -> "Basic realm=\"Sentinel Admins\"")
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Authentication required to access resource.")
            }

            "not change the user verification status" in {
              // TODO: find a way to use matcher without using Await explicitly
              val results = Await.result(servlet.users.getUser(UserExamples.unverified.id), 1000.milli)
              results must beSome.like { case user => user.verified must beFalse }
            }
          }
        }

        "when he/she authenticates correctly" >> {
          br

          "when the patch contains an op with path '/verified' should" >> inline {

            new Context.PriorRequestsClean {

              def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/verified", true)))
              def request = () => patch(endpoint(userRecord.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 403" in {
                priorResponse.status mustEqual 403
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must /("message" -> "Unauthorized to access resource.")
              }

              "not change the user verification status" in {
                // TODO: find a way to use matcher without using Await explicitly
                val results = Await.result(servlet.users.getUser(userRecord.id), 1000.milli)
                results must beSome.like { case user => user.verified must beFalse }
              }
            }
          }

          "when the patch contains an op with path '/password' should" >> inline {

            val newPass = "newPass123"

            new Context.PriorRequestsClean {

              def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/password", newPass)))
              def request = () => patch(endpoint(userRecord.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 403" in {
                priorResponse.status mustEqual 403
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must /("message" -> "Unauthorized to access resource.")
              }

              "not change the user password" in {
                // TODO: find a way to use matcher without using Await explicitly
                val results = servlet.users.getUser(userRecord.id)
                Await.result(results, 1000.milli) must beSome.like { case user =>
                  user.passwordMatches(newPass) must beFalse
                  user.passwordMatches(password) must beTrue
                }
              }
            }
          }

          "when the patch contains an op with path '/email' should" >> inline {

            val newEmail = "new@email.com"

            new Context.PriorRequestsClean {

              def payload = toJsonByteArray(Seq(SinglePathPatch("replace", "/email", newEmail)))
              def request = () => patch(endpoint(userRecord.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 403" in {
                priorResponse.status mustEqual 403
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must /("message" -> "Unauthorized to access resource.")
              }

              "not change the user email" in {
                // TODO: find a way to use matcher without using Await explicitly
                val results = Await.result(servlet.users.getUser(userRecord.id), 1000.milli)
                results must beSome.like { case user => user.email mustEqual userRecord.email }
              }
            }
          }
        }
      }
    }
  }
}
