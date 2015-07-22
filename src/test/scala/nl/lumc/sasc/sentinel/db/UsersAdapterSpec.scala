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
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import nl.lumc.sasc.sentinel.models.User

class UsersAdapterSpec extends Specification with Mockito {

  /** User object for testing. */
  private val testUserObj = User("testId", "test@email.com", "testPass", "testKey", verified = true, isAdmin = false)

  /** MongoDB testing database name. */
  private val testDbName = "users_test"

  /** MongoDB representation of the user object. */
  private val testUserDbo = grater[User].asDBObject(testUserObj)

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  /** Method to retrieve the underlying collection used by the user adapter. */
  private def getColl(mockDb: Fongo) = mockDb
    .getDB(testDbName)
    .getCollection(MongodbConnector.CollectionNames.Users)

  /** Helper method to create a new adapter instance for each test. */
  private def testAdapter(mockDb: Fongo) =
    new UsersAdapter {
      val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)
    }

  "usersExist" should {

    "return false when database is empty" in {
      testAdapter(makeFongo).userExist("myId") must beFalse
    }

    "return false when the user ID does not exist" in {
      val fongo = makeFongo
      val coll = getColl(fongo)
      coll.insert(testUserDbo)
      val adapter = testAdapter(fongo)
      val testId = "testIdAnother"
      testUserDbo.get("id") must be_!=(testId)
      adapter.userExist(testId) must beFalse
    }

    "return true when the user ID exists" in {
      val fongo = makeFongo
      val coll = getColl(fongo)
      coll.insert(testUserDbo)
      val adapter = testAdapter(fongo)
      val testId = "testId"
      testUserDbo.get("id") mustEqual testId
      adapter.userExist(testId) must beTrue
    }
  }
}

