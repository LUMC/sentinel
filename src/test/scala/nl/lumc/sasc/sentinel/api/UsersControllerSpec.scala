package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.json4s.jackson.Serialization.write
import org.scalatra.test.specs2._
import org.specs2.execute.Failure
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{ ApiMessage, UserRequest }

class UsersControllerSpec extends ScalatraSpec with SentinelServletSpec with Mockito {

  def is = s2"""

  POST /users must
    return status 400 with the correct message if request body is empty               $postRunsEmptyBody
    return status 400 with the correct message if request body is not valid JSON      $postRunsInvalidBody
    return status 400 with the correct message if passwords are not equal             $postRunsPasswordDiff
    return status 400 with the correct message if user ID is too short                $postRunsUserIdTooShort
    return status 400 with the correct message if password is too short               $postRunsPasswordTooShort
    return status 400 with the correct message if password does not have upper case   $postRunsPasswordNoUpper
    return status 400 with the correct message if password does not have lower case   $postRunsPasswordNoLower
    return status 400 with the correct message if password does not have numbers      $postRunsPasswordNoNumber
    return status 409 with the correct message if user ID already exists              $postRunsUserIdExists
    return status 201 with the correct message if user creation is successful         $postRunsUserCreated
"""

  private def toByteArray[T <: AnyRef](obj: T) = write(obj).getBytes

  implicit val swagger = new SentinelSwagger
  implicit val mongo = makeDbAccess

  val servlet = new UsersController
  addServlet(servlet, "/users/*")

  def postRunsEmptyBody = post("/users", body = Array.empty[Byte]) {
    status mustEqual 400
    apiMessage mustEqual Some(ApiMessage("Malformed user request."))
  }

  def postRunsInvalidBody = post("/users", Array[Byte](10, 20, 30)) {
    status mustEqual 400
    apiMessage mustEqual Some(ApiMessage("Malformed user request."))
  }

  def postRunsPasswordDiff = {
    val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MyPass123", "Mypass456"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("Different passwords given.")))
    }
  }

  def postRunsUserIdTooShort = {
    val payload = toByteArray(UserRequest("hm", "mail@mail.com", "Mypass123", "Mypass123"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("User ID too short.")))
    }
  }

  def postRunsPasswordTooShort = {
    val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "My1", "My1"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.", Seq("Password too short.")))
    }
  }

  def postRunsPasswordNoUpper = {
    val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "mypass123", "mypass123"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.",
        Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
    }
  }

  def postRunsPasswordNoLower = {
    val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "MYPASS123", "MYPASS123"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.",
        Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
    }
  }

  def postRunsPasswordNoNumber = {
    val payload = toByteArray(UserRequest("yeah", "mail@mail.com", "Mypass", "Mypass"))
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("Invalid user request.",
        Seq("Password does not contain a mixture of lower case(s), upper case(s), and number(s).")))
    }
  }

  def postRunsUserIdExists = {
    val userRequest = UserRequest("yeah", "mail@mail.com", "Mypass123", "Mypass123")
    val payload = toByteArray(userRequest)
    post("/users", payload) {
      status mustEqual 400
      apiMessage mustEqual Some(ApiMessage("User ID already taken."))
    } before {
      servlet.users.addUser(userRequest.user)
    } after {
      servlet.users.deleteUser(userRequest.user.id)
    }
  }

  def postRunsUserCreated = {
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
    } after {
      servlet.users.deleteUser(userRequest.user.id)
    }

  }
}
