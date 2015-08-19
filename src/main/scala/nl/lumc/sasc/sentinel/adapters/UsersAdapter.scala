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

import scala.concurrent._

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.{ User, UserPatch }
import nl.lumc.sasc.sentinel.utils.exceptions.ExistingUserIdException

/** Trait for performing operations on user records. */
trait UsersAdapter extends MongodbAdapter with FutureAdapter {

  /** Overridable execution context for this adapter. */
  protected def usersAdapterContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = usersAdapterContext

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Users)

  /** Checks whether the given user ID exist or not. */
  def userExist(userId: String): Future[Boolean] = Future {
    coll.find(MongoDBObject("id" -> userId)).limit(1).size == 1
  }

  /** Adds a new user record. */
  def addUser(user: User): Future[Unit] = userExist(user.id)
    .map { exists =>
      if (exists) throw new ExistingUserIdException("User ID '" + user.id + "' already exists.")
      else coll.insert(grater[User].asDBObject(user))
    }

  /** Deletes a user record. */
  def deleteUser(userId: String): Unit = coll
    // TODO: what to do with the user's uploaded runs?
    // TODO: refactor to use Futures instead
    .remove(MongoDBObject("id" -> userId))

  /** Retrieves the record of the given user ID. */
  def getUser(userId: String): Future[Option[User]] = Future {
    coll
      .findOne(MongoDBObject("id" -> userId))
      .collect { case dbo => grater[User].asObject(dbo) }
  }

  /**
   * Patches an existing user record without saving the patched user to the database.
   *
   * @param user User object to apply the patches to.
   * @param patchOps Patch operations.
   * @return Either a sequence of error messages or the patched user object.
   */
  def patchUser(user: User, patchOps: Seq[UserPatch]): \/[Seq[String], User] =
    patchOps.foldLeft(user.right[Seq[String]]) {
      case (usr, p) =>
        for {
          u <- usr
          patchedUsr <- p.apply(u)
        } yield patchedUsr
    }

  /** Updates an existing user record in the database. */
  def updateUser(user: User): Future[WriteResult] = Future {
    coll
      .update(MongoDBObject("id" -> user.id), grater[User].asDBObject(user), upsert = false)
  }

  /**
   * Applies the given patch operations to an existing user in the database.
   *
   * @param userId ID of the user to patch.
   * @param patchOps Patch operations to apply.
   * @return Either error messages or write result.
   */
  def patchAndUpdateUser(userId: String, patchOps: Seq[UserPatch]): Future[Seq[String] \/ WriteResult] = {
    val result = for {
      currentUser <- EitherT(getUser(userId).map {
        case Some(u) => u.right[Seq[String]]
        case None    => Seq(s"User ID '$userId' not found.").left[User]
      })
      patchedUser <- EitherT(Future { patchUser(currentUser, patchOps) })
      writeResult <- EitherT(updateUser(patchedUser).map(_.right[Seq[String]]))
    } yield writeResult

    result.run
  }
}
