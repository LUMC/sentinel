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

import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.exceptions.ExistingUserIdException

class UsersAdapterSpec extends Specification with Mockito {

  /** User object for testing. */
  private val testUserObj = User("testId", "test@email.com", "testPass", "testKey", verified = true, isAdmin = false)

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
}

