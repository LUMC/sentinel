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
package nl.lumc.sasc.sentinel.adapters

import com.github.fakemongo.Fongo
import com.mongodb.DBCollection
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.scalaz.DisjunctionMatchers

import nl.lumc.sasc.sentinel.models.{ CommonMessages, User, UserPatch }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject
import nl.lumc.sasc.sentinel.utils.exceptions.ExistingUserIdException

class UsersAdapterSpec extends Specification
    with DisjunctionMatchers
    with Mockito {

  /** User object for testing. */
  private val testUserObj =
    User("testId", "test@email.com", User.hashPassword("testPass"), "testKey", verified = true, isAdmin = false)

  /** MongoDB testing database name. */
  private val testDbName = "users_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  /** Method to retrieve the underlying collection used by the user adapter. */
  private def getColl(mockDb: Fongo) = mockDb
    .getDB(testDbName)
    .getCollection(MongodbAdapter.CollectionNames.Users)

  class TestUsersAdapter(mockDb: Fongo) extends UsersAdapter {

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[UsersAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbAdapter.CollectionNames.Users)
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestUsersAdapter(mockDb)

  /** Helper method to create a new adapter instance for each test. */
  private def usingAdapter(fongo: Fongo)(f: DBCollection => Any) = {
    f(getColl(fongo))
    makeAdapter(fongo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def testAdapter = makeAdapter(makeFongo)

  /** MongoDB representation of the user object. */
  private val testUserDbo = grater[User].asDBObject(testUserObj)

  "usersExist" should {

    "return false when database is empty" in {
      testAdapter.userExist("myId") must beFalse.await
    }

    "return false when the user ID does not exist" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      val testId = "testIdAnother"
      testUserDbo.get("id") must be_!=(testId)
      adapter.userExist(testId) must beFalse.await
    }

    "return true when the user ID exists" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      val testId = "testId"
      testUserDbo.get("id") mustEqual testId
      adapter.userExist(testId) must beTrue.await
    }
  }

  "addUser" should {

    "succeed when supplied with a non-existing user" in {
      val adapter = testAdapter
      adapter.addUser(testUserObj) must beEqualTo(()).await
      adapter.find(MongoDBObject("id" -> testUserObj.id)).count mustEqual 1
    }

    "fail when supplied with an existing user" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      adapter.find(MongoDBObject("id" -> testUserObj.id)).count mustEqual 1
      adapter.addUser(testUserObj) must throwAn[ExistingUserIdException].await
      adapter.find(MongoDBObject("id" -> testUserObj.id)).count mustEqual 1
    }
  }

  "getUser" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Users) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getUser(testUserObj.id) must throwA[RuntimeException].await
    }

    "succeed returning none when the database is empty" in {
      testAdapter.getUser(testUserObj.id) must beEqualTo(None).await
    }

    "succeed returning the user when supplied with an existing user" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      adapter.getUser(testUserObj.id) must beEqualTo(Some(testUserObj)).await
    }

    "succeed returning none when supplied with a non-existing user" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj.copy(id = "newId"))
        coll.insert(dbo)
      }
      adapter.getUser(testUserObj.id) must beEqualTo(None).await
    }
  }

  "patchUser" should {

    "return the user unchanged when patchOps is empty" in {
      testAdapter.patchUser(testUserObj, List.empty) must beRightDisjunction.like {
        case user =>
          user mustEqual testUserObj
      }
    }

    "return a user with updated email when patchOps has one email patch operation" in {
      val newEmail = "new@email.com"
      val patches = List(UserPatch("replace", "/email", newEmail))
      testAdapter.patchUser(testUserObj, patches) must beRightDisjunction.like {
        case user =>
          user mustEqual testUserObj.copy(email = newEmail)
      }
    }

    "return a user with updated password when patchOps has one password patch operation" in {
      val newPw = "myNewPass123"
      val patches = List(UserPatch("replace", "/password", newPw))
      testUserObj.passwordMatches(newPw) must beFalse
      testAdapter.patchUser(testUserObj, patches) must beRightDisjunction.like {
        case user =>
          user.passwordMatches(newPw) must beTrue
      }
    }

    "return a user with updated verification status when patchOps has one verification status patch operation" in {
      val newStatus = !testUserObj.verified
      val patches = List(UserPatch("replace", "/verified", newStatus))
      testAdapter.patchUser(testUserObj, patches) must beRightDisjunction.like {
        case user =>
          user mustEqual testUserObj.copy(verified = newStatus)
      }
    }

    "return an updated user as expected when patchOps has multiple valid patches" in {
      val patch1 = UserPatch("replace", "/verified", false)
      val patch2 = UserPatch("replace", "/email", "my@email.com")
      val patch3 = UserPatch("replace", "/password", "SuperSecret126")
      testAdapter.patchUser(testUserObj, List(patch1, patch2, patch3)) must beRightDisjunction.like {
        case user =>
          user.email mustEqual "my@email.com"
          user.verified must beFalse
          user.passwordMatches("SuperSecret126") must beTrue
      }
    }

    "return the correct error message when op is invalid" in {
      val patches = List(UserPatch("add", "/email", "t@t.com"))
      testAdapter.patchUser(testUserObj, patches) must beLeftDisjunction.like {
        case errs =>
          errs mustEqual CommonMessages.PatchValidationError(List("Unexpected operation: 'add'."))
      }
    }

    "return the correct error message when path is invalid" in {
      val patches = List(UserPatch("replace", "/invalid", 100))
      testAdapter.patchUser(testUserObj, patches) must beLeftDisjunction.like {
        case errs =>
          errs mustEqual CommonMessages.PatchValidationError(List("Invalid path: '/invalid'."))
      }
    }

    "return the correct error message when the value for a valid path is invalid" in {
      val invalidCombinations = List(
        ("/verified", 1),
        ("/verified", "yes"),
        ("/password", 1235),
        ("/email", true))
      foreach(invalidCombinations) {
        case (path, value) =>
          testAdapter.patchUser(testUserObj, List(UserPatch("replace", path, value))) must beLeftDisjunction.like {
            case errs =>
              errs mustEqual CommonMessages.PatchValidationError(List(s"Invalid value for path '$path': '$value'."))
          }
      }
    }
  }

  "updateUser" should {

    "fail when the database is empty" in {
      testAdapter.updateUser(testUserObj) must throwAn[Exception].await
    }

    "fail when the database does not contain the specified user" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj.copy(id = "newId"))
        coll.insert(dbo)
      }
      adapter.updateUser(testUserObj) must throwAn[Exception].await
    }

    "succeed when the database contain the specified user" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj)
        coll.insert(dbo)
      }
      val newEmail = "new@email.com"
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 0
      adapter.updateUser(testUserObj.copy(email = newEmail)).map { res => res.getN mustEqual 1 }.await
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 1
    }
  }

  "patchAndUpdateUser" should {

    "return the expected error message when user does not exist" in {
      testAdapter.patchAndUpdateUser("nonexistent", List.empty).map { ret =>
        ret must beLeftDisjunction.like {
          case errs => errs mustEqual CommonMessages.MissingUserId("nonexistent")
        }
      }.await
    }

    "return the expected result when patch is successful" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj)
        coll.insert(dbo)
      }
      val newEmail = "new@email.com"
      val newStatus = !testUserObj.verified
      val patch1 = UserPatch("replace", "/verified", newStatus)
      val patch2 = UserPatch("replace", "/email", newEmail)
      val patches = List(patch1, patch2)
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 0
      adapter.find(MongoDBObject("verified" -> newStatus)).count mustEqual 0
      adapter.patchAndUpdateUser(testUserObj.id, patches).map { ret =>
        ret must beRightDisjunction.like {
          case res => res.getN mustEqual 1
        }
      }.await
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 1
      adapter.find(MongoDBObject("verified" -> newStatus)).count mustEqual 1
    }
  }
}

