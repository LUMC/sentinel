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

import scalaz.NonEmptyList

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.specs2.specification.core.Fragments

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.testing.{ MimeType, SentinelServletSpec, UserExamples }
import nl.lumc.sasc.sentinel.models.{ Payloads, SinglePathPatch, User, UserRequest }

class UsersControllerSpec extends SentinelServletSpec {

  def makeBasicAuthHeader(user: User, password: String): String =
    "Basic " + BaseEncoding.base64().encode(s"${user.id}:$password".getBytes(Charsets.UTF_8))

  val servlet = new UsersController()(swagger, dao)
  val baseEndpoint = "/users"
  addServlet(servlet, s"$baseEndpoint/*")

  s"OPTIONS '$baseEndpoint'" >> {
    br
    "when using the default parameters" should ctx.optionsReq(baseEndpoint, "HEAD,POST")
  }; br

  s"POST '$baseEndpoint'" >> {
  br

    val uReq1 = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
    val ctx1 = HttpContext(() => post(baseEndpoint, uReq1.toByteArray) { response })
    "when a valid user creation request is sent" should ctx.priorReqsOnCleanDb(ctx1) { http =>

      "return status 201" in {
        http.rep.status mustEqual 201
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "New user created.")
        http.rep.body must /("hints") /# 0 / s"uri: /users/${uReq1.id}"
        http.rep.body must /("hints") /# 1 / """apiKey: \S+""".r
      }
    }

    val ctx2 = HttpContext(() => post(baseEndpoint, body = Array.empty[Byte]) { response })
    "when the request body is empty" should ctx.priorReqsOnCleanDb(ctx2) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Malformed user request.")
      }
    }

    val ctx3 = HttpContext(() => post(baseEndpoint, body = Array[Byte](10, 20, 30)) { response })
    "when the request body is not valid JSON" should ctx.priorReqsOnCleanDb(ctx3) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Malformed user request.")
      }
    }

    val uReq4 = UserRequest("yeah", "mail@mail.com", "MyPass123", "MyPass456")
    val ctx4 = HttpContext(() => post(baseEndpoint, uReq4.toByteArray) { response })
    "when the passwords do not match" should ctx.priorReqsOnCleanDb(ctx4) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") / "Different passwords given."
      }
    }

    val uReq5 = UserRequest("hm", "mail@mail.com", "MyPass123", "Mypass123")
    val ctx5 = HttpContext(() => post(baseEndpoint, uReq5.toByteArray) { response })
    "when the user ID is less than 3 characters" should ctx.priorReqsOnCleanDb(ctx5) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") / "User ID shorter than 3 characters."
      }
    }

    "when the user ID contains forbidden characters" >> {
    br

      Fragments.foreach(Seq(" ", ".", "*23#%")) { forbiddenChars =>

        val uReq6 = UserRequest(s"yeah$forbiddenChars", "mail@mail.com", "Mypass123", "Mypass123")
        val ctx6 = HttpContext(() => post(baseEndpoint, uReq6.toByteArray) { response })
        s"such as '$forbiddenChars'" should ctx.priorReqsOnCleanDb(ctx6) { http =>

          "return status 400" in {
            http.rep.status mustEqual 400
          }

          "return a JSON object containing the expected message" in {
            http.rep.contentType mustEqual MimeType.Json
            http.rep.body must /("message" -> "Invalid user request.")
            http.rep.body must /("hints") / "User ID contains forbidden characters: .+".r
          }
        }
      }
    }

    val uReq7 = UserRequest("yeah", "mail@mail.com", "MylaB", "MylaB")
    val ctx7 = HttpContext(() => post(baseEndpoint, uReq7.toByteArray) { response })
    "when the password is less than 6 characters" should ctx.priorReqsOnCleanDb(ctx7) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") / "Password shorter than 6 characters."
      }
    }

    val uReq8 = UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123")
    val ctx8 = HttpContext(() => post(baseEndpoint, uReq8.toByteArray) { response })
    "when the password does not contain any uppercase characters" should ctx.priorReqsOnCleanDb(ctx8) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") /
          "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
      }
    }

    val uReq9 = UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123")
    val ctx9 = HttpContext(() => post(baseEndpoint, uReq9.toByteArray) { response })
    "when the password does not contain any lowercase characters" should ctx.priorReqsOnCleanDb(ctx9) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") /
          "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
      }
    }

    val uReq10 = UserRequest("yeah", "mail@mail.com", "MyPass", "MyPass")
    val ctx10 = HttpContext(() => post(baseEndpoint, uReq10.toByteArray) { response })
    "when the password does not contain any numeric characters" should ctx.priorReqsOnCleanDb(ctx10) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Invalid user request.")
        http.rep.body must /("hints") /
          "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
      }
    }

    val uReq11 = UserRequest("yeah", "mail@mail.com", "MyPass123", "MyPass123")
    val ctx11 = HttpContext(NonEmptyList(
      () => post(baseEndpoint, uReq11.toByteArray) { response },
      () => post(baseEndpoint, uReq11.toByteArray) { response }))
    "when the requested user ID already exists" should ctx.priorReqsOnCleanDb(ctx11) { http =>

      "return status 409" in {
        http.reps.last.status mustEqual 409
      }

      "return a JSON object containing the expected message" in {
        http.reps.last.contentType mustEqual MimeType.Json
        http.reps.last.body must /("message" -> "User ID already taken.")
        http.reps.last.body must /("hints") /# 0 / startWith(s"Existing ID: ${uReq11.id}.")
      }
    }
  }

  s"OPTIONS '$baseEndpoint/:userRecordId'" >> {
  br
    "when using the default parameters" should ctx.optionsReq(s"$baseEndpoint/:userRecordId", "GET,HEAD,PATCH")
  }; br

  s"GET '$baseEndpoint/:userRecordId'" >> {
  br

    def endpoint(userRecordId: String) = s"$baseEndpoint/$userRecordId"

    val ctx1 = HttpContext(() => get(endpoint("")) { response })
    "when the user record ID is not specified" should ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "User record ID not specified.")
      }
    }

    val ctx2 = HttpContext(() => get(endpoint(UserExamples.avg.id)) { response })
    "when the user ID is not specified" should ctx.priorReqsOnCleanDb(ctx2, populate = true) { http =>

      "return status 400" in {
        http.rep.status mustEqual 400
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> Payloads.UnspecifiedUserIdError.message)
      }
    }

    val ctx3 = HttpContext(() => get(endpoint(UserExamples.avg.id),
      Seq(("userId", UserExamples.avg.id))) { response })
    s"when the user does not provide the $HeaderApiKey header" should
      ctx.priorReqsOnCleanDb(ctx3, populate = true) { http =>

        "return status 401" in {
          http.rep.status mustEqual 401
        }

        "return the challenge response header key" in {
          http.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
        }

        "return a JSON object containing the expected message" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("message" -> Payloads.AuthenticationError.message)
        }
    }

    val ctx4 = HttpContext(() => get(endpoint(UserExamples.avg.id),
      Seq(("userId", UserExamples.avg.id)), Map(HeaderApiKey -> (UserExamples.avg.activeKey + "no"))) { response })
    s"when the provided $HeaderApiKey does not match the one owned by the user" should
      ctx.priorReqsOnCleanDb(ctx4, populate = true) { http =>

        "return status 401" in {
          http.rep.status mustEqual 401
        }

        "return the challenge response header key" in {
          http.rep.header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
        }

        "return a JSON object containing the expected message" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("message" -> Payloads.AuthenticationError.message)
        }
    }

    val ctx5 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
      Seq(("userId", UserExamples.unverified.id)), Map(HeaderApiKey -> UserExamples.unverified.activeKey)) { response })
    "when an authenticated, unverified user requests a user record" should
      ctx.priorReqsOnCleanDb(ctx5, populate = true) { http =>

        "return status 403" in {
          http.rep.status mustEqual 403
        }

        "return a JSON object containing the expected message" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("message" -> "Unauthorized to access resource.")
        }
    }

    val ctx6 = HttpContext(() => get(endpoint(UserExamples.avg.id),
      Seq(("userId", UserExamples.avg.id)), Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
    "when a verified, authenticated user requests his/her own record" should
      ctx.priorReqsOnCleanDb(ctx6, populate = true) { http =>

        "return status 200" in {
          http.rep.status mustEqual 200
        }

        "return a JSON object containing the expected attributes" in {
          http.rep.contentType mustEqual MimeType.Json
          http.rep.body must /("id" -> UserExamples.avg.id)
          http.rep.body must /("email" -> UserExamples.avg.email)
          http.rep.body must /("activeKey" -> UserExamples.avg.activeKey)
        }
    }

    val ctx7 = HttpContext(() => get(endpoint(UserExamples.admin.id),
      Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
    "when an admin requests his/her own record" should ctx.priorReqsOnCleanDb(ctx7, populate = true) { http =>

      "return status 200" in {
        http.rep.status mustEqual 200
      }

      "return a JSON object containing the expected attributes" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("id" -> UserExamples.admin.id)
        http.rep.body must /("email" -> UserExamples.admin.email)
        http.rep.body must /("activeKey" -> UserExamples.admin.activeKey)
      }
    }

    val ctx8 = HttpContext(() => get(endpoint(UserExamples.avg.id),
      Seq(("userId", UserExamples.admin.id)), Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
    "when an admin requests another user's record" should ctx.priorReqsOnCleanDb(ctx8, populate = true) { http =>

      "return status 200" in {
        http.rep.status mustEqual 200
      }

      "return a JSON object containing the expected attributes" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("id" -> UserExamples.avg.id)
        http.rep.body must /("email" -> UserExamples.avg.email)
        http.rep.body must /("activeKey" -> UserExamples.avg.activeKey)
      }
    }

    val ctx9 = HttpContext(() => get(endpoint(UserExamples.avg2.id),
      Seq(("userId", UserExamples.avg.id)), Map(HeaderApiKey -> UserExamples.avg.activeKey)) { response })
    "when an nonadmin requests another user's record" should ctx.priorReqsOnCleanDb(ctx9, populate = true) { http =>

      "return status 403" in {
        http.rep.status mustEqual 403
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual MimeType.Json
        http.rep.body must /("message" -> "Unauthorized to access resource.")
      }
    }
  }

  s"PATCH '$baseEndpoint/:userRecordId'" >> {
  br

    def endpoint(userRecordId: String) = s"$baseEndpoint/$userRecordId"

    val ctx1 = HttpContext(() => patch(endpoint(UserExamples.avg.id)) { response })
    "when the user ID is not specified" should ctx.priorReqsOnCleanDb(ctx1, populate = true) { http =>

      "return status 401" in {
        http.rep.status mustEqual 401
      }

      "return the challenge response header key" in {
        http.rep.header must havePair("WWW-Authenticate" -> "Basic realm=\"Sentinel Admins\"")
      }

      "return a JSON object containing the expected message" in {
        http.rep.contentType mustEqual "application/json"
        http.rep.body must /("message" -> "Authentication required to access resource.")
      }
    }

    "using basic HTTP authentication" >> {
    br

      "using a valid user" >> {
      br

        val userSets = Seq(
          ("an admin user", "0PwdAdmin", UserExamples.admin),
          ("a non-admin user to his/her own account", "0PwdAvg", UserExamples.avg))

        Fragments.foreach(userSets) { case (utype, pwd, uobj) =>

          s"such as $utype" >> {

            def headers = Map("Authorization" -> makeBasicAuthHeader(uobj, pwd))

            val payload1 = Seq(SinglePathPatch("replace", "/verified", true))
            val ictx1 = HttpContext(() => patch(endpoint(UserExamples.unverified.id), payload1.toByteArray,
              Map("Authorization" -> makeBasicAuthHeader(uobj, s"${pwd}_nomatch"))) { response })
            br; "when the password does not match" should ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

              "return status 401" in {
                ihttp.rep.status mustEqual 401
              }

              "return the challenge response header key" in {
                ihttp.rep.header must havePair("WWW-Authenticate" -> "Basic realm=\"Sentinel Admins\"")
              }

              "return a JSON object containing the expected message" in {
                ihttp.rep.contentType mustEqual "application/json"
                ihttp.rep.body must /("message" -> "Authentication required to access resource.")
              }

              val iictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
                Seq(("userId", UserExamples.admin.id)),
                Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
              "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                "return the verification status unchanged" in {
                  iihttp.rep.body must /("verified" -> false)
                }
              }
            }

            "using the correct authentication" >> {
            br

              val ictx0 = HttpContext(() => patch(endpoint(""),
                Seq(SinglePathPatch("replace", "/verified", true)).toByteArray, headers) { response })
              "when the user record ID is not specified" should ctx.priorReqsOnCleanDb(ictx0, populate = true) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> "User record ID not specified.")
                }
              }

              val ictx1 = HttpContext(() => patch(endpoint(uobj.id), Array[Byte](10, 20, 30), headers) { response })
              "when the patch document is non-JSON" should ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual "application/json"
                  ihttp.rep.body must /("message" -> "JSON is invalid.")
                  ihttp.rep.body must /("hints") /# 0 / "Invalid syntax."
                }
              }

              val ictx2 = HttpContext(() => patch(endpoint(uobj.id),
                Seq.empty[SinglePathPatch].toByteArray, headers) { response })
              "when the patch document is an empty list" should ctx.priorReqsOnCleanDb(ictx2, populate = true) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> Payloads.JsonValidationError.message)
                  ihttp.rep.body must /("hints") /# 0 / startWith("error: array is too short")
                }
              }

              val ictx3 = HttpContext(() => patch(endpoint(uobj.id),
                Array.empty[Byte], headers) { response })
              "when the patch document is empty" should ctx.priorReqsOnCleanDb(ictx3, populate = true) { ihttp =>

                "return status 400" in {
                  ihttp.rep.status mustEqual 400
                }

                "return a JSON object containing the expected message" in {
                  ihttp.rep.contentType mustEqual MimeType.Json
                  ihttp.rep.body must /("message" -> Payloads.JsonValidationError.message)
                  ihttp.rep.body must /("hints") /# 0 / "Nothing to parse."
                }
              }

              val ictx4 = HttpContext(() => patch(endpoint(uobj.id),
                Seq("yalala", SinglePathPatch("replace", "/password", "newPass123")).toByteArray, headers) { response })
              "when the patch document contains an invalid entry" should
                ctx.priorReqsOnCleanDb(ictx4, populate = true) { ihttp =>

                  "return status 400" in {
                    ihttp.rep.status mustEqual 400
                  }

                  "return a JSON object containing the expected message" in {
                    ihttp.rep.contentType mustEqual MimeType.Json
                    ihttp.rep.body must /("message" -> "JSON is invalid.")
                    ihttp.rep.body must /("hints") /# 0 / startWith("error: instance failed to match")
                  }
                }

              "using forbidden 'op' values" >> {
              br

                Fragments.foreach(Seq("add", "remove", "test")) { op =>

                  val ictx5 = HttpContext(() => patch(endpoint(uobj.id), Seq(
                    SinglePathPatch("replace", "/password", "newPass123"),
                    SinglePathPatch(op, "/password", "newPass123")).toByteArray, headers) { response })
                  s"such as '$op'" should ctx.priorReqsOnCleanDb(ictx5, populate = true) { ihttp =>

                    "return status 400" in {
                      ihttp.rep.status mustEqual 400
                    }

                    "return a JSON object containing the expected message" in {
                      ihttp.rep.contentType mustEqual MimeType.Json
                      ihttp.rep.body must /("message" -> "Invalid patch operation(s).")
                      ihttp.rep.body must /("hints") /# 0 / s"Unsupported operation: '$op'."
                    }
                  }
                }
              }

              val ictx6 = HttpContext(() => patch(endpoint(UserExamples.unverified.id),
                Seq(SinglePathPatch("replace", "/verified", true)).toByteArray, headers) { response })
              "when the patch contains an op with path '/verified' and targets another user" should
                ctx.priorReqsOnCleanDb(ictx6, populate = true) { ihttp =>

                  if (uobj == UserExamples.admin) {

                    "return status code 204" in {
                      ihttp.rep.status mustEqual 204
                    }

                    "return an empty JSON body" in {
                      ihttp.rep.contentType mustEqual MimeType.Json
                      ihttp.rep.body must beEmpty
                    }

                    val iictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
                      Seq(("userId", UserExamples.admin.id)),
                      Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
                    "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                      "return an updated verification status" in {
                        iihttp.rep.body must /("verified" -> true)
                      }
                    }

                  } else {
                    "return status code 403" in {
                      ihttp.rep.status mustEqual 403
                    }

                    "return a JSON body containing the expected message" in {
                      ihttp.rep.contentType mustEqual MimeType.Json
                      ihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
                    }

                    val iictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
                      Seq(("userId", UserExamples.admin.id)),
                      Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
                    "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                      "return the verification status unchanged" in {
                        iihttp.rep.body must /("verified" -> false)
                      }
                    }
                  }
                }

              val ictx7 = HttpContext(() => patch(endpoint(uobj.id),
                Seq(SinglePathPatch("replace", "/verified", false)).toByteArray, headers) { response })
              "when the patch contains an op with path '/verified' and targets the same user" should
                ctx.priorReqsOnCleanDb(ictx7, populate = true) { ihttp =>

                  if (uobj == UserExamples.admin) {

                    "return status code 204" in {
                      ihttp.rep.status mustEqual 204
                    }

                    "return an empty JSON body" in {
                      ihttp.rep.contentType mustEqual MimeType.Json
                      ihttp.rep.body must beEmpty
                    }

                    val iictx1 = HttpContext(() => get(endpoint(uobj.id),
                      Seq(("userId", UserExamples.admin.id)),
                      Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
                    "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>

                      "return status 401" in {
                        iihttp.rep.status mustEqual 403
                      }

                      "return a JSON object containing the expected message" in {
                        iihttp.rep.contentType mustEqual MimeType.Json
                        iihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
                      }
                    }

                  } else {
                    "return status code 403" in {
                      ihttp.rep.status mustEqual 403
                    }

                    "return a JSON body containing the expected message" in {
                      ihttp.rep.contentType mustEqual MimeType.Json
                      ihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
                    }

                    val iictx1 = HttpContext(() => get(endpoint(uobj.id),
                      Seq(("userId", UserExamples.admin.id)),
                      Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
                    "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                      "return the verification status unchanged" in {
                        iihttp.rep.body must /("verified" -> true)
                      }
                    }
                  }
                }

              val newEmail = "new@email.com"
              val ictx8 = HttpContext(() => patch(endpoint(uobj.id),
                Seq(SinglePathPatch("replace", "/email", newEmail)).toByteArray, headers) { response })
              "when the patch contains an op with path '/email' and targets the same user" should
                ctx.priorReqsOnCleanDb(ictx8, populate = true) { iihttp =>

                  "return status 204" in {
                    iihttp.rep.status mustEqual 204
                  }

                  "return an empty JSON body" in {
                    iihttp.rep.contentType mustEqual MimeType.Json
                    iihttp.rep.body must beEmpty
                  }

                  val iictx1 = HttpContext(() => get(endpoint(uobj.id),
                    Seq(("userId", uobj.id)),
                    Map(HeaderApiKey -> uobj.activeKey)) { response })
                  "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                    "return an updated email" in {
                      iihttp.rep.body must /("email" -> newEmail)
                    }
                  }
                }

              val newPass = "newPass123"
              val ictx9 = HttpContext(() => patch(endpoint(uobj.id),
                Seq(SinglePathPatch("replace", "/password", newPass)).toByteArray, headers) { response })
              "when the patch contains an op with path '/password' and targets the same user" should
                ctx.priorReqsOnCleanDb(ictx9, populate = true) { iihttp =>

                  "return status 204" in {
                    iihttp.rep.status mustEqual 204
                  }

                  "return an empty JSON body" in {
                    iihttp.rep.contentType mustEqual MimeType.Json
                    iihttp.rep.body must beEmpty
                  }

                  // Mock request that uses HTTP auth
                  val iictx1 = HttpContext(() => patch(endpoint(uobj.id),
                    Seq(SinglePathPatch("replace", "/email", uobj.email)).toByteArray,
                    Map("Authorization" -> makeBasicAuthHeader(uobj, newPass))) { response })
                  "when a new request using the new password is done afterwards" should
                    ctx.priorReqs(iictx1) { iihttp =>
                      "return status 20x" in {
                        iihttp.rep.status mustEqual 204
                      }
                  }
                }
            }
          }
        }
      }

      "using an unverified user" >> {
        br

        val uobj = UserExamples.unverified
        val pwd = "0PwdUnverified"
        def headers = Map("Authorization" -> makeBasicAuthHeader(uobj, pwd))

        val payload1 = Seq(SinglePathPatch("replace", "/verified", true))
        val ictx1 = HttpContext(() => patch(endpoint(UserExamples.unverified.id), payload1.toByteArray,
          Map("Authorization" -> makeBasicAuthHeader(uobj, s"${pwd}_nomatch"))) { response })
        "when the password does not match" should ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

          "return status 401" in {
            ihttp.rep.status mustEqual 401
          }

          "return the challenge response header key" in {
            ihttp.rep.header must havePair("WWW-Authenticate" -> "Basic realm=\"Sentinel Admins\"")
          }

          "return a JSON object containing the expected message" in {
            ihttp.rep.contentType mustEqual "application/json"
            ihttp.rep.body must /("message" -> "Authentication required to access resource.")
          }

          val iictx1 = HttpContext(() => get(endpoint(UserExamples.unverified.id),
            Seq(("userId", UserExamples.admin.id)),
            Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
          "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
            "return the verification status unchanged" in {
              iihttp.rep.body must /("verified" -> false)
            }
          }
        }

        "using the correct authentication" >> {
        br

          val ictx1 = HttpContext(() => patch(endpoint(uobj.id),
            Seq(SinglePathPatch("replace", "/verified", false)).toByteArray, headers) { response })
          "when the patch contains an op with path '/verified' and targets the same user" should
            ctx.priorReqsOnCleanDb(ictx1, populate = true) { ihttp =>

              "return status code 403" in {
                ihttp.rep.status mustEqual 403
              }

              "return a JSON body containing the expected message" in {
                ihttp.rep.contentType mustEqual MimeType.Json
                ihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
              }

              val iictx1 = HttpContext(() => get(endpoint(uobj.id),
                Seq(("userId", UserExamples.admin.id)),
                Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
              "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                "return the verification status unchanged" in {
                  iihttp.rep.body must /("verified" -> false)
                }
              }
            }

          val newEmail = "new@email.com"
          val ictx2 = HttpContext(() => patch(endpoint(uobj.id),
            Seq(SinglePathPatch("replace", "/email", newEmail)).toByteArray, headers) { response })
          "when the patch contains an op with path '/email' and targets the same user" should
            ctx.priorReqsOnCleanDb(ictx2, populate = true) { ihttp =>

              "return status code 403" in {
                ihttp.rep.status mustEqual 403
              }

              "return a JSON body containing the expected message" in {
                ihttp.rep.contentType mustEqual MimeType.Json
                ihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
              }

              val iictx1 = HttpContext(() => get(endpoint(uobj.id),
                Seq(("userId", UserExamples.admin.id)),
                Map(HeaderApiKey -> UserExamples.admin.activeKey)) { response })
              "when the supposedly patched user is queried afterwards" should ctx.priorReqs(iictx1) { iihttp =>
                "return the email unchanged" in {
                  iihttp.rep.body must not /("email" -> newEmail)
                  iihttp.rep.body must /("email" -> UserExamples.unverified.email)
                }
              }
            }

          val newPass = "newPass123"
          val ictx3 = HttpContext(() => patch(endpoint(uobj.id),
            Seq(SinglePathPatch("replace", "/password", newPass)).toByteArray, headers) { response })
          "when the patch contains an op with path '/password' and targets the same user" should
            ctx.priorReqsOnCleanDb(ictx3, populate = true) { ihttp =>

              "return status code 403" in {
                ihttp.rep.status mustEqual 403
              }

              "return a JSON body containing the expected message" in {
                ihttp.rep.contentType mustEqual MimeType.Json
                ihttp.rep.body must /("message" -> Payloads.AuthorizationError.message)
              }

              // Mock request that uses HTTP auth
              val iictx1 = HttpContext(() => patch(endpoint(uobj.id),
                Seq(SinglePathPatch("replace", "/email", uobj.email)).toByteArray,
                Map("Authorization" -> makeBasicAuthHeader(uobj, newPass))) { response })
              "when a new request using the new password is done afterwards" should
                ctx.priorReqs(iictx1) { iihttp =>
                  "return a non 20x status" in {
                    iihttp.rep.status mustNotEqual 204
                  }
                }
            }
        }
      }
    }
  }
}
