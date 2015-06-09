package nl.lumc.sasc.sentinel.db

import scala.util.Try

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.{ User, UserPatch }

trait UsersAdapter extends MongodbConnector {

  private lazy val coll = mongo.db(collectionNames.Users)

  def userExist(userId: String): Boolean = coll.find(MongoDBObject("id" -> userId)).limit(1).size == 1

  def addUser(user: User): Unit = coll.insert(grater[User].asDBObject(user))

  def deleteUser(userId: String): Unit = coll.remove(MongoDBObject("id" -> userId))

  def getUser(userId: String): Option[User] = coll
    .findOne(MongoDBObject("id" -> userId))
    .collect { case dbo => grater[User].asObject(dbo) }

  def patchUser(user: User, patchOps: Seq[UserPatch]): User = patchOps.foldLeft(user)((u, p) => p.apply(u))

  def updateUser(user: User) = coll
    .update(MongoDBObject("id" -> user.id), grater[User].asDBObject(user), upsert = false)

  def patchAndUpdateUser(userId: String, patchOps: Seq[UserPatch]) =
    for {
      user <- getUser(userId)
      patchedUser <- Try(patchUser(user, patchOps)).toOption
      updateOp <- Try(updateUser(patchedUser)).toOption
    } yield updateOp
}
