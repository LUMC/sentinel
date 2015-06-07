package nl.lumc.sasc.sentinel.api

import org.json4s.jackson.Serialization.write

import nl.lumc.sasc.sentinel.models.UserRequest

class UsersControllerSpec extends SentinelServletSpec {

  private def toByteArray[T <: AnyRef](obj: T) = write(obj).getBytes
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
}
