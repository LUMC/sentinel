package nl.lumc.sasc.sentinel.db

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.User

trait UsersAdapter extends MongodbConnector {

  val usersCollectionName = CollectionNames.Users

  private lazy val coll = mongo.db(usersCollectionName)

  def userExist(userId: String): Boolean = coll.find(MongoDBObject("id" -> userId)).limit(1).size == 1

  def addUser(user: User): Unit = coll.insert(grater[User].asDBObject(user))

  def deleteUser(userId: String): Unit = coll.remove(MongoDBObject("id" -> userId))

  def getUser(userId: String): Option[User] = coll
    .findOne(MongoDBObject("id" -> userId))
    .collect { case dbo => grater[User].asObject(dbo) }
}
