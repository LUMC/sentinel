package nl.lumc.sasc.sentinel.db

import scala.util.Try

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.{ User, UserPatch }

/** Trait for performing operations on user records. */
trait UsersAdapter extends MongodbConnector {

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Users)

  /** Checks whether the given user ID exist or not. */
  def userExist(userId: String): Boolean = coll
    // TODO: refactor to use Futures instead
    .find(MongoDBObject("id" -> userId)).limit(1).size == 1

  /** Adds a new user record. */
  def addUser(user: User): Unit = coll
    // TODO: refactor to use Futures instead
    .insert(grater[User].asDBObject(user))

  /** Deletes a user record. */
  def deleteUser(userId: String): Unit = coll
    // TODO: what to do with the user's uploaded runs?
    // TODO: refactor to use Futures instead
    .remove(MongoDBObject("id" -> userId))

  /** Retrieves the record of the given user ID. */
  def getUser(userId: String): Option[User] = coll
    // TODO: refactor to use Futures instead
    .findOne(MongoDBObject("id" -> userId))
    .collect { case dbo => grater[User].asObject(dbo) }

  /**
   * Patches an existing user record without saving the patched user to the database.
   *
   * @param user User object to apply the patches to.
   * @param patchOps Patch operations.
   * @return Patched user.
   */
  def patchUser(user: User, patchOps: Seq[UserPatch]): User = patchOps
    // TODO: refactor to use Futures instead
    .foldLeft(user)((u, p) => p.apply(u))

  /** Updates an existing user record in the database. */
  def updateUser(user: User) = coll
    // TODO: refactor to use Futures instead
    .update(MongoDBObject("id" -> user.id), grater[User].asDBObject(user), upsert = false)

  /**
   * Applies the given patch operations to an existing user in the database.
   *
   * @param userId ID of the user to patch.
   * @param patchOps Patch operations to apply.
   * @return Write result.
   */
  def patchAndUpdateUser(userId: String, patchOps: Seq[UserPatch]) =
    // TODO: refactor to use Futures instead
    for {
      user <- getUser(userId)
      patchedUser <- Try(patchUser(user, patchOps)).toOption
      updateOp <- Try(updateUser(patchedUser)).toOption
    } yield updateOp
}
