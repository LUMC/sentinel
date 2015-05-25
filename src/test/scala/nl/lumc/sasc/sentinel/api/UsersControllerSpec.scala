package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.json4s.jackson.Serialization.write
import org.scalatra.test.specs2._
import org.specs2.execute.Failure
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{ ApiMessage, UserRequest }

class UsersControllerSpec extends SentinelServletSpec with Mockito {

  private def toByteArray[T <: AnyRef](obj: T) = write(obj).getBytes
  implicit val swagger = new SentinelSwagger
  implicit val mongo = dbAccess
  val servlet = new UsersController
  addServlet(servlet, "/users/*")

  "POST '/users'" >> {

    br

    "when the request body is empty" should {
      "return status 400 and the correct message" in {
        post("/users", body = Array.empty[Byte]) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Malformed user request."))
        }
      }
    }

    "when the request body is not valid JSON" should {
      "return status 400 and the correct message" in {
        post("/users", Array[Byte](10, 20, 30)) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Malformed user request."))
        }
      }
    }

    "when the passwords do not match" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "Mypass456"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("Different passwords given.")))
        }
      }
    }

    "when the user ID is less than 3 characters" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("hm", "mail@mail.com", "Mypass123", "Mypass123"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("User ID too short.")))
        }
      }
    }

    "when the password is less than 6 characters" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "My1aB", "My1aB"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("Password too short.")))
        }
      }
    }

    "when the password does not contain uppercase" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.",
            Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
        }
      }
    }

    "when the password does not contain lowercase" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.",
            Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
        }
      }
    }

    "when the password does not contain numbers" should {
      "return status 400 and the correct message" in {
        val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "Mypass", "Mypass"))
        post("/users", payload) {
          status mustEqual 400
          apiMessage mustEqual Some(ApiMessage("Invalid user request.",
            Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
        }
      }
    }

    "when the requested user ID already exists in the database" should {
      "return status 409 and the correct message" in {
        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toByteArray(userRequest)
        post("/users", payload) {
          status mustEqual 409
          apiMessage mustEqual Some(ApiMessage("User ID already taken."))
        } before {
          servlet.users.addUser(userRequest.user)
        } after { resetDb() }
      }
    }

    "when the user is succesfully created" should {
      "return status 201 and the correct message" in {
        val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
        val payload = toByteArray(userRequest)
        post("/users", payload) {
          status mustEqual 201
          apiMessage.isDefined must beTrue
          apiMessage.get.message mustEqual "New user created."
          val msgData = (jsonBody.get \ "data").extract[Map[String, String]]
          msgData("uri") mustEqual "/users/yeah"
          msgData.keySet must contain("apiKey")
        } before {
          servlet.users.userExist("yeah") must beFalse
        } after { resetDb() }
      }
    }
  }
}
