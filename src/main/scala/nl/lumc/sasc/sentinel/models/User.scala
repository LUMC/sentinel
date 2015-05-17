package nl.lumc.sasc.sentinel.models

import java.util.Date

case class User(
  id: String,
  email: String,
  hashedPassword: String,
  isConfirmed: Boolean,
  isAdmin: Boolean,
  creationTime: Date,
  updateTime: Option[Date])

