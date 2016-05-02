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

import scala.concurrent._

import com.mongodb.casbah.Imports._
import com.novus.salat._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.{ ApiPayload, User }
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.Payloads._
import nl.lumc.sasc.sentinel.models.JsonPatch._

/** Trait for performing operations on user records. */
trait UsersAdapter extends FutureMongodbAdapter {

  /** Context for Salat conversions. */
  implicit val SalatContext = nl.lumc.sasc.sentinel.utils.SentinelSalatContext

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
  def addUser(user: User): Future[Perhaps[WriteResult]] = userExist(user.id)
    .map { exists =>
      if (exists) DuplicateUserIdError(user.id).left
      else coll.insert(grater[User].asDBObject(user)).right
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

  /** Updates an existing user record in the database. */
  def updateUser(user: User): Future[Perhaps[WriteResult]] = Future {
    val wr = coll
      .update(MongoDBObject("id" -> user.id), grater[User].asDBObject(user), upsert = false)
    if (wr.getN == 1) wr.right
    else UnexpectedDatabaseError("Update failed.").left
  }

  /**
   * Applies the given patch operations to an existing user in the database.
   *
   * @param user The user performing the patch operation.
   * @param userId ID of the user to patch.
   * @param patchOps Patch operations to apply
   * @return Either error messages or write result.
   */
  def patchAndUpdateUser(user: User, userId: String, patchOps: List[PatchOp]): Future[Perhaps[WriteResult]] = {
    val patchActions = for {
      // Make sure the user is authorized to perform the operations
      reqByAdmin <- ? <~ (if (!(user.id == userId || user.isAdmin)) AuthorizationError.left else user.isAdmin.right)
      // Make sure only admins perform verifications
      _ <- ? <~ (if (patchOps.exists(_.path == "/verified") && !reqByAdmin) AuthorizationError.left else ().right)
      currentUser <- ? <~ getUser(userId).map(_.toRightDisjunction(UserIdNotFoundError(userId)))
      patchedUser <- ? <~ patchUser(currentUser, patchOps)
      writeResult <- ? <~ updateUser(patchedUser)
    } yield writeResult

    patchActions.run
  }

  /**
   * Patches an existing user record without saving the patched user to the database.
   *
   * @param user User object to apply the patches to.
   * @param patchOps Patch operations.
   * @return Either a sequence of error messages or the patched user object.
   */
  def patchUser(user: User, patchOps: List[PatchOp]): Perhaps[User] =
    patchOps.foldLeft(user.right[ApiPayload]) {
      case (usr, patch) =>
        for {
          u <- usr
          patchedUsr <- patchFunctions((u, patch))
        } yield patchedUsr
    }

  /** Function that will be applied to user objects. */
  val patchFunctions: PatchFunction[User] = {

    case (user, ReplaceOp("/verified", v: Boolean)) =>
      user.copy(verified = v).right

    case (user, ReplaceOp("/email", v: String)) =>
      val vMessages = User.Validator.emailMessages(v)
      if (vMessages.isEmpty) user.copy(email = v).right
      else PatchValidationError(vMessages).left

    case (user, ReplaceOp("/password", v: String)) =>
      val vMessages = User.Validator.passwordMessages(v, v)
      if (vMessages.isEmpty) user.copy(hashedPassword = User.hashPassword(v)).right
      else PatchValidationError(vMessages).left

    case (_, invalidPatch) => PatchValidationError(invalidPatch).left
  }
}
