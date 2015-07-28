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
package nl.lumc.sasc.sentinel.db

import com.github.fakemongo.Fongo
import com.mongodb.DBCollection
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import nl.lumc.sasc.sentinel.models.{ User, UserPatch }
import nl.lumc.sasc.sentinel.utils.exceptions.ExistingUserIdException

class UsersAdapterSpec extends Specification with Mockito {

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
    .getCollection(MongodbConnector.CollectionNames.Users)

  class TestUsersAdapter(mockDb: Fongo) extends UsersAdapter {

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[UsersAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbConnector.CollectionNames.Users)
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestUsersAdapter(mockDb)

  /** Helper method to create a new adapter instance for each test. */
  private def usingAdapter(fongo: Fongo)(f: DBCollection => Any) = {
    f(getColl(fongo))
    makeAdapter(fongo)
  }

  /** MongoDB representation of the user object. */
  private val testUserDbo = grater[User].asDBObject(testUserObj)

  "usersExist" should {

    "return false when database is empty" in {
      makeAdapter(makeFongo).userExist("myId") must beFalse.await
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
      val adapter = makeAdapter(makeFongo)
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
      mockDb.getCollection(MongodbConnector.CollectionNames.Users) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getUser(testUserObj.id) must throwA[RuntimeException].await
    }

    "succeed returning none when the database is empty" in {
      val adapter = makeAdapter(makeFongo)
      adapter.getUser(testUserObj.id) must beEqualTo(None).await
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

    "not change the input user when patchOps is empty" in {
      val adapter = makeAdapter(makeFongo)
      adapter.patchUser(testUserObj, Seq.empty) must beRight.like { case user =>
        user mustEqual testUserObj
      }
    }

    "change the user email when patchOps has one email patch operation" in {
      val adapter = makeAdapter(makeFongo)
      val newEmail = "new@email.com"
      val patches = Seq(UserPatch("replace", "/email", newEmail))
      adapter.patchUser(testUserObj, patches) must beRight.like { case user =>
        user mustEqual testUserObj.copy(email = newEmail)
      }
    }

    "change the user password when patchOps has one password patch operation" in {
      val adapter = makeAdapter(makeFongo)
      val newPw = "myNewPass123"
      val patches = Seq(UserPatch("replace", "/password", newPw))
      val newUser = adapter.patchUser(testUserObj, patches)
      testUserObj.passwordMatches(newPw) must beFalse
      newUser must beRight.like { case user =>
        user.passwordMatches(newPw) must beTrue
      }
    }

    "change the user verification status when patchOps has one verification status patch operation" in {
      val adapter = makeAdapter(makeFongo)
      val newStatus = !testUserObj.verified
      val patches = Seq(UserPatch("replace", "/verified", newStatus))
      adapter.patchUser(testUserObj, patches) must beRight.like { case user =>
        user mustEqual testUserObj.copy(verified = newStatus)
      }
    }
  }
}

