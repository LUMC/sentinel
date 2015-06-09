package nl.lumc.sasc.sentinel.api

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.json4s.jackson.Serialization.write

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.models.{UserPatch, UserRequest}

class UsersControllerSpec extends SentinelServletSpec {

  private def toByteArray[T <: AnyRef](obj: T) = write(obj).getBytes
  private def makeBasicAuthHeader(userId: String, password: String): String =
    "Basic " + BaseEncoding.base64().encode(s"$userId:$password".getBytes(Charsets.UTF_8))
  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val servlet = new UsersController
  val baseEndpoint = "/users"
  addServlet(servlet, s"$baseEndpoint/*")

  s"POST '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the user is successfully created should" >> inline {

      new Context.PriorRequestsClean {

        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toByteArray(userRequest)
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 201" in {
          priorResponse.status mustEqual 201
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "New user created.")
          priorResponse.body must /("data") /("uri" -> ("/users/" + userRequest.id))
          priorResponse.body must /("data") /("apiKey" -> """\S+""".r)
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

        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "Mypass456"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") / "Different passwords given."
        }
      }
    }

    "when the user ID is less than 3 characters should" >> inline {

      new Context.PriorRequestsClean {

        val payload = toByteArray(UserRequest("hm", "mail@mail.com", "MyPass123", "Mypass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") / "User ID shorter than 3 characters."
        }
      }
    }

    "when the user ID contains disallowed characters which" can {

      Seq(" ", ".", "*23#%") foreach { nonchar =>

        s"be '$nonchar' should" >> inline {
          
          new Context.PriorRequestsClean {
            val payload = toByteArray(UserRequest("yeah" + nonchar, "mail@mail.com", "Mypass123", "Mypass123"))
            def request = () => post(endpoint, payload) { response }
            def priorRequests = Seq(request)

            "return status 400" in {
              priorResponse.status mustEqual 400
            }

            "return a JSON object containing the expected message" in {
              priorResponse.body must /("message" -> "Invalid user request.")
              priorResponse.body must /("data") / "User ID contains disallowed characters: .+".r
            }
          }
        }
      }
    }

    "when the password is less than 6 characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "My1aB", "My1aB"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") / "Password shorter than 6 characters."
        }
      }
    }

    "when the password does not contain any uppercase characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain any lowercase characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain any numeric characters should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass", "MyPass"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.body must /("message" -> "Invalid user request.")
          priorResponse.body must /("data") /
            "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the requested user ID already exists should" >> inline {

      new Context.PriorRequestsClean {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "MyPass123"))
        def request = () => post(endpoint, payload) { response }
        def priorRequests = Seq(request, request)

        "return status 409" in {
          priorResponses.last.status mustEqual 409
        }

        "return a JSON object containing the expected message" in {
          priorResponses.last.body must /("message" -> "User ID already taken.")
        }
      }
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

        def request = () => get(endpoint(Users.avg.id)) { response }
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

        def request = () => get(endpoint(Users.avg.id), Seq(("userId", Users.avg.id))) { response }
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

        def params = Seq(("userId", Users.avg.id))
        def headers = Map(HeaderApiKey -> (user.activeKey + "nono"))
        def request = () => get(endpoint(Users.avg.id), params, headers) { response }
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

        def params = Seq(("userId", Users.unverified.id))
        def headers = Map(HeaderApiKey -> Users.unverified.activeKey)
        def request = () => get(endpoint(Users.unverified.id), params, headers) { response }
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

          def params = Seq(("userId", Users.avg.id))
          def headers = Map(HeaderApiKey -> Users.avg.activeKey)
          def request = () => get(endpoint(Users.avg.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> Users.avg.id)
            priorResponse.body must /("email" -> Users.avg.email)
            priorResponse.body must /("activeKey" -> Users.avg.activeKey)
          }
        }
      }

      "and the user is an admin that requests another user's record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", Users.admin.id))
          def headers = Map(HeaderApiKey -> Users.admin.activeKey)
          def request = () => get(endpoint(Users.avg.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> Users.avg.id)
            priorResponse.body must /("email" -> Users.avg.email)
            priorResponse.body must /("activeKey" -> Users.avg.activeKey)
          }
        }
      }

      "and the user is an admin that requests his/her own record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", Users.admin.id))
          def headers = Map(HeaderApiKey -> Users.admin.activeKey)
          def request = () => get(endpoint(Users.admin.id), params, headers = headers) { response }
          def priorRequests = Seq(request)

          "return status 200" in {
            priorResponse.status mustEqual 200
          }

          "return a JSON object containing the expected attributes" in {
            priorResponse.contentType mustEqual "application/json"
            priorResponse.body must /("id" -> Users.admin.id)
            priorResponse.body must /("email" -> Users.admin.email)
            priorResponse.body must /("activeKey" -> Users.admin.activeKey)
          }
        }
      }

      "and the user is not an admin and requests someone else's record should" >> inline {

        new Context.PriorRequestsClean {

          def params = Seq(("userId", Users.avg.id))
          def headers = Map(HeaderApiKey -> Users.avg.activeKey)
          def request = () => get(endpoint(Users.avg2.id), params, headers = headers) { response }
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

        def payload = toByteArray(Seq(UserPatch("replace", "/password", "newPass123")))
        def request = () => patch(endpoint(Users.avg.id), payload) { response }
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

    //"when the specified user record ID does not exist should" >> inline {}

    //"when the specified user ID does not exist should" >> inline {}

    "when the patch document is non-JSON should" >> inline {

      new Context.PriorRequestsClean {

        def request = () => patch(endpoint(Users.avg.id), Array[Byte](10, 20 ,30)) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "File is not JSON-formatted.")
        }
      }
    }

    "when the patch document is empty should" >> inline {

      new Context.PriorRequestsClean {

        def payload = toByteArray(Seq())
        def request = () => patch(endpoint(Users.avg.id), payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "Invalid user patch.")
          priorResponse.body must /("data" -> "Operations can not be empty.")
        }
      }
    }

    "when the patch document contains an invalid entry should" >> inline {

      new Context.PriorRequestsClean {

        def payload = toByteArray(Seq("yalala", UserPatch("replace", "/password", "newPass123")))
        def request = () => patch(endpoint(Users.avg.id), payload) { response }
        def priorRequests = Seq(request)

        "return status 400" in {
          priorResponse.status mustEqual 400
        }

        "return a JSON object containing the expected message" in {
          priorResponse.contentType mustEqual "application/json"
          priorResponse.body must /("message" -> "JSON run summary is invalid.")
        }
      }
    }

    "when the patch document contains disallowed 'op' values which" can {

      Seq("add", "remove", "test") foreach { case op =>

        s"be '$op' should" >> inline {

          new Context.PriorRequestsClean {

            def payload = toByteArray(
              Seq(UserPatch("replace", "/password", "newPass123"), UserPatch(op, "/password", "newPass123")))
            def request = () => patch(endpoint(Users.avg.id), payload) { response }
            def priorRequests = Seq(request)

            "return status 400" in {
              priorResponse.status mustEqual 400
            }

            "return a JSON object containing the expected message" in {
              priorResponse.contentType mustEqual "application/json"
              priorResponse.body must /("message" -> "Invalid user patch.")
            }
          }
        }
      }
    }

    "using basic HTTP authentication" >> {
      br

      "when done by an admin user" >> {
        br

        def headers = Map("Authorization" -> makeBasicAuthHeader(Users.admin.id, "0PwdAdmin"))

        "when the password does not match should" >> inline {

          def headers = Map("Authorization" -> makeBasicAuthHeader(Users.admin.id, "0PwdAdmin_nomatch"))

          new Context.PriorRequestsClean {

            def payload = toByteArray(Seq(UserPatch("replace", "/verified", true)))
            def request = () => patch(endpoint(Users.unverified.id), payload, headers) { response }
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

            "not change the user record" in {
              servlet.users.getUser(Users.unverified.id) must beSome.like { case user => user.verified must beFalse }
            }
          }
        }

        "when he/she authenticates correctly" >> {
          br

          "when the patch contains an op with path '/verified' should" >> inline {

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/verified", true)))
              def request = () => patch(endpoint(Users.unverified.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 204" in {
                priorResponse.status mustEqual 204
              }

              "return an empty body" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must beEmpty
              }

              "change the user verified status" in {
                servlet.users.getUser(Users.unverified.id) must beSome.like { case user => user.verified must beTrue }
              }
            }
          }

          "when the patch contains an op with path '/password' should" >> inline {

            val newPass = "newPass123"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/password", newPass)))
              def request = () => patch(endpoint(Users.avg.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 204" in {
                priorResponse.status mustEqual 204
              }

              "return an empty body" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must beEmpty
              }

              "change the user password" in {
                servlet.users.getUser(Users.avg.id) must beSome.like { case user =>
                  user.passwordMatches(newPass) must beTrue
                  user.passwordMatches("0PwdAvg") must beFalse
                }
              }
            }
          }

          "when the patch contains an op with path '/email' should" >> inline {

            val newEmail = "new@email.com"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/email", newEmail)))
              def request = () => patch(endpoint(Users.avg.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 204" in {
                priorResponse.status mustEqual 204
              }

              "return an empty body" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must beEmpty
              }

              "change the user email" in {
                servlet.users.getUser(Users.avg.id) must beSome.like { case user => user.email mustEqual newEmail }
              }
            }
          }
        }
      }

      "when done by an non-admin user to his/her own account" >> {
        br

        def headers = Map("Authorization" -> makeBasicAuthHeader(Users.avg.id, "0PwdAvg"))

        "when the password does not match should" >> inline {

          def headers = Map("Authorization" -> makeBasicAuthHeader(Users.avg.id, "0PwdAvg_nomatch"))

          new Context.PriorRequestsClean {

            def payload = toByteArray(Seq(UserPatch("replace", "/verified", true)))
            def request = () => patch(endpoint(Users.unverified.id), payload, headers) { response }
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

            "not change the user record" in {
              servlet.users.getUser(Users.unverified.id) must beSome.like { case user => user.verified must beFalse }
            }
          }
        }

        "when he/she authenticates correctly" >> {
          br

          "when the patch contains an op with path '/verified' should" >> inline {

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/verified", false)))
              def headers = Map("Authorization" -> makeBasicAuthHeader(Users.avg.id, "0PwdAvg"))
              def request = () => patch(endpoint(Users.avg.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 403" in {
                priorResponse.status mustEqual 403
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must /("message" -> "Unauthorized to access resource.")
              }

              "not change the user record" in {
                servlet.users.getUser(Users.avg.id) must beSome.like { case user => user.verified must beTrue }
              }
            }
          }

          "when the patch contains an op with path '/password' should" >> inline {

            val newPass = "newPass123"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/password", newPass)))
              def request = () => patch(endpoint(Users.avg.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 204" in {
                priorResponse.status mustEqual 204
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must beEmpty
              }

              "not change the user password" in {
                servlet.users.getUser(Users.avg.id) must beSome.like { case user =>
                  user.passwordMatches(newPass) must beTrue
                  user.passwordMatches("0PwdAvg") must beFalse
                }
              }
            }
          }

          "when the patch contains an op with path '/email' should" >> inline {

            val newEmail = "new@email.com"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/email", newEmail)))
              def request = () => patch(endpoint(Users.avg.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 204" in {
                priorResponse.status mustEqual 204
              }

              "return an empty body" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must beEmpty
              }

              "change the user email" in {
                servlet.users.getUser(Users.avg.id) must beSome.like { case user => user.email mustEqual newEmail }
              }
            }
          }
        }
      }

      "when done by an unverified user" >> {
        br

        def password = "0PwdUnverified"
        def userRecord = Users.unverified
        def headers = Map("Authorization" -> makeBasicAuthHeader(userRecord.id, password))

        "when the password does not match should" >> inline {

          def headers = Map("Authorization" -> makeBasicAuthHeader(userRecord.id, (password + "_nomatch")))

          new Context.PriorRequestsClean {

            def payload = toByteArray(Seq(UserPatch("replace", "/verified", true)))
            def request = () => patch(endpoint(user.id), payload, headers) { response }
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

            "not change the user record" in {
              servlet.users.getUser(userRecord.id) must beSome.like { case usr => usr.verified must beFalse }
            }
          }
        }

        "when he/she authenticates correctly" >> {
          br

          "when the patch contains an op with path '/verified' should" >> inline {

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/verified", true)))
              def request = () => patch(endpoint(userRecord.id), payload, headers) { response }
              def priorRequests = Seq(request)

              "return status 403" in {
                priorResponse.status mustEqual 403
              }

              "return a JSON object containing the expected message" in {
                priorResponse.contentType mustEqual "application/json"
                priorResponse.body must /("message" -> "Unauthorized to access resource.")
              }

              "not change the user record" in {
                servlet.users.getUser(userRecord.id) must beSome.like { case usr => usr.verified must beFalse }
              }
            }
          }

          "when the patch contains an op with path '/password' should" >> inline {

            val newPass = "newPass123"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/password", newPass)))
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
                servlet.users.getUser(userRecord.id) must beSome.like { case user =>
                  user.passwordMatches(newPass) must beFalse
                  user.passwordMatches(password) must beTrue
                }
              }
            }
          }

          "when the patch contains an op with path '/email' should" >> inline {

            val newEmail = "new@email.com"

            new Context.PriorRequestsClean {

              def payload = toByteArray(Seq(UserPatch("replace", "/email", newEmail)))
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
                servlet.users.getUser(userRecord.id) must beSome.like { case usr => usr.email must not be equalTo(newEmail) }
              }
            }
          }
        }
      }
    }
  }
}
