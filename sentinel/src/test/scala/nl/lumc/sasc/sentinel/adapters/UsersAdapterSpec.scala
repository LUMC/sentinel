/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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

import scala.concurrent.duration._

import com.github.fakemongo.Fongo
import com.mongodb.DBCollection
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import nl.lumc.sasc.sentinel.models.{ Payloads, User }
import nl.lumc.sasc.sentinel.models.JsonPatch
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class UsersAdapterSpec extends Specification
    with Mockito {

  sequential

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

  /** How many times a Future-based method should retry until we mark it as a failure. */
  private val asyncRetries: Int = 5

  /** How many times a Future-based method should wait until we mark it as a failure. */
  private val asyncWait: FiniteDuration = 1000.millisecond

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

    "return false when database is empty" in { implicit ee: ExecutionEnv =>
      testAdapter.userExist("myId") must beFalse.await(asyncRetries, asyncWait)
    }

    "return false when the user ID does not exist" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      val testId = "testIdAnother"
      testUserDbo.get("id") must be_!=(testId)
      adapter.userExist(testId) must beFalse.await(asyncRetries, asyncWait)
    }

    "return true when the user ID exists" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      val testId = "testId"
      testUserDbo.get("id") mustEqual testId
      adapter.userExist(testId) must beTrue.await(asyncRetries, asyncWait)
    }
  }

  br
  "addUser" should {

    "succeed when supplied with a non-existing user" in { implicit ee: ExecutionEnv =>
      val adapter = testAdapter
      adapter.addUser(testUserObj).map { ret =>
        ret.toEither must beRight.like { case res => res.getN mustEqual 1 }
      }.await(asyncRetries, asyncWait)
    }

    "fail when supplied with an existing user" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      adapter.find(MongoDBObject("id" -> testUserObj.id)).count mustEqual 1
      adapter.addUser(testUserObj).map { ret =>
        ret.toEither must beLeft.like {
          case err =>
            err mustEqual Payloads.DuplicateUserIdError(testUserObj.id)
        }
      }.await(asyncRetries, asyncWait)
      adapter.find(MongoDBObject("id" -> testUserObj.id)).count mustEqual 1
    }
  }

  br
  "getUser" should {

    "fail when the database operation raises any exception" in { implicit ee: ExecutionEnv =>
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.FongoDB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Users) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getUser(testUserObj.id) must throwA[RuntimeException].await(asyncRetries, asyncWait)
    }

    "succeed returning none when the database is empty" in { implicit ee: ExecutionEnv =>
      testAdapter.getUser(testUserObj.id) must beEqualTo(None).await(asyncRetries, asyncWait)
    }

    "succeed returning the user when supplied with an existing user" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testUserDbo)
      }
      adapter.getUser(testUserObj.id) must beEqualTo(Some(testUserObj)).await(asyncRetries, asyncWait)
    }

    "succeed returning none when supplied with a non-existing user" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj.copy(id = "newId"))
        coll.insert(dbo)
      }
      adapter.getUser(testUserObj.id) must beEqualTo(None).await(asyncRetries, asyncWait)
    }
  }

  br
  "patchUser" should {

    "return the user unchanged when patchOps is empty" in { implicit ee: ExecutionEnv =>
      testAdapter.patchUser(testUserObj, List.empty).toEither must beRight.like {
        case user =>
          user mustEqual testUserObj
      }
    }

    "return an updated user as expected when patchOps has multiple valid patches" in { implicit ee: ExecutionEnv =>
      val patch1 = JsonPatch.ReplaceOp("/verified", false)
      val patch2 = JsonPatch.ReplaceOp("/email", "my@email.com")
      val patch3 = JsonPatch.ReplaceOp("/password", "SuperSecret126")
      testAdapter.patchUser(testUserObj, List(patch1, patch2, patch3)).toEither must beRight.like {
        case user =>
          user.email mustEqual "my@email.com"
          user.verified must beFalse
          user.passwordMatches("SuperSecret126") must beTrue
      }
    }

    "return the correct error message when the value for a valid path is unexpected" in { implicit ee: ExecutionEnv =>
      val invalidCombinations = List(
        JsonPatch.ReplaceOp("/verified", 1),
        JsonPatch.ReplaceOp("/verified", "yes"),
        JsonPatch.ReplaceOp("/password", 1235),
        JsonPatch.ReplaceOp("/email", true))
      foreach(invalidCombinations) {
        case invalidPatch =>
          testAdapter.patchUser(testUserObj, List(invalidPatch)).toEither must beLeft.like {
            case errs => errs mustEqual Payloads.PatchValidationError(invalidPatch)
          }
      }
    }
  }

  br
  "updateUser" should {

    "fail when the database is empty" in { implicit ee: ExecutionEnv =>
      testAdapter.updateUser(testUserObj).map { ret =>
        ret.toEither must beLeft.like { case err => err == Payloads.UnexpectedDatabaseError("Update failed.") }
      }.await(asyncRetries, asyncWait)
    }

    "fail when the database does not contain the specified user" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj.copy(id = "newId"))
        coll.insert(dbo)
      }
      adapter.updateUser(testUserObj).map { ret =>
        ret.toEither must beLeft.like { case err => err == Payloads.UnexpectedDatabaseError("Update failed.") }
      }.await(asyncRetries, asyncWait)
    }

    "succeed when the database contains the specified user" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj)
        coll.insert(dbo)
      }
      val newEmail = "new@email.com"
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 0
      adapter.updateUser(testUserObj.copy(email = newEmail)).map { res =>
        res.toEither must beRight.like { case wr => wr.getN mustEqual 1 }
      }.await(asyncRetries, asyncWait)
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 1
    }
  }

  br
  "patchAndUpdateUser" should {

    "return the expected error message when user does not exist" in { implicit ee: ExecutionEnv =>
      testAdapter.patchAndUpdateUser(testUserObj.copy(isAdmin = true), "nonexistent", List.empty).map { ret =>
        ret.toEither must beLeft.like {
          case errs => errs mustEqual Payloads.UserIdNotFoundError("nonexistent")
        }
      }.await(asyncRetries, asyncWait)
    }

    "return the expected result when patch is successful" in { implicit ee: ExecutionEnv =>
      val adapter = usingAdapter(makeFongo) { coll =>
        val dbo = grater[User].asDBObject(testUserObj)
        coll.insert(dbo)
      }
      val newEmail = "new@email.com"
      val newStatus = !testUserObj.verified
      val patch1 = JsonPatch.ReplaceOp("/verified", newStatus)
      val patch2 = JsonPatch.ReplaceOp("/email", newEmail)
      val patches = List(patch1, patch2)
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 0
      adapter.find(MongoDBObject("verified" -> newStatus)).count mustEqual 0
      adapter.patchAndUpdateUser(testUserObj.copy(isAdmin = true), testUserObj.id, patches).map { ret =>
        ret.toEither must beRight.like {
          case res => res.getN mustEqual 1
        }
      }.await(asyncRetries, asyncWait)
      adapter.find(MongoDBObject("email" -> newEmail)).count mustEqual 1
      adapter.find(MongoDBObject("verified" -> newStatus)).count mustEqual 1
    }
  }
}