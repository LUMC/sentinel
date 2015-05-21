package nl.lumc.sasc.sentinel.models

import java.util.Date

import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt

case class User(
    _id: ObjectId,
    id: String,
    email: String,
    hashedPassword: String,
    emailVerified: Boolean,
    isAdmin: Boolean,
    creationTime: Date,
    updateTime: Option[Date] = None) {

  def passwordMatches(candidate: String): Boolean = BCrypt.checkpw(candidate, hashedPassword)
}

