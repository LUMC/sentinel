package nl.lumc.sasc.sentinel.api

import org.json4s.jackson.Serialization.write

import nl.lumc.sasc.sentinel.SentinelServletSpec
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

    "when the user is succesfully created" should {
      "return status 201 and the correct message" in {
        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toByteArray(userRequest)
        post(baseEndpoint, payload) {
          status mustEqual 201
          body must /("message" -> "New user created.")
          body must /("data") /("uri" -> ("/users/" + userRequest.id))
          body must /("data") /("apiKey" -> ".+".r)
        } before {
          servlet.users.userExist("yeah") must beFalse
        } after { resetDatabase() }
      }
    }

    "when the request body is empty" should {
      "return status 400 and the correct message" in {
        post(baseEndpoint, body = Array.empty[Byte]) {
          status mustEqual 400
          body must /("message" -> "Malformed user request.")
        }
      }
    }

    "when the request body is not valid JSON" should {
      "return status 400 and the correct message" in {
        post(baseEndpoint, Array[Byte](10, 20, 30)) {
          status mustEqual 400
          body must /("message" -> "Malformed user request.")
        }
      }
    }

    "when the passwords do not match" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "Mypass456"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "Different passwords given."
        }
      }
    }

    "when the user ID is less than 3 characters" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("hm", "mail@mail.com", "Mypass123", "Mypass123"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "User ID shorter than 3 characters."
        }
      }
    }

    "when the password is less than 6 characters" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "My1aB", "My1aB"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "Password shorter than 6 characters."
        }
      }
    }

    "when the password does not contain uppercase" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain lowercase" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the password does not contain numbers" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "Mypass", "Mypass"))
        post(baseEndpoint, payload) {
          status mustEqual 400
          body must /("message" -> "Invalid user request.")
          body must /("data") / "Password does not contain a mixture of lower case(s), upper case(s), and number(s)."
        }
      }
    }

    "when the requested user ID already exists in the database" should {
      "return status 409 and the correct message" in {
        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toByteArray(userRequest)
        post(baseEndpoint, payload) {
          status mustEqual 409
          body must /("message" -> "User ID already taken.")
        } before {
          servlet.users.addUser(userRequest.user)
        } after { resetDatabase() }
      }
    }
  }
}
