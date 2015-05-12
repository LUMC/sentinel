package nl.lumc.sasc.sentinel.models

import java.util.Date

case class User(
  id: String,
  email: String,
  isConfirmed: Boolean,
  isAdmin: Boolean,
  nRuns: Int,
  creationTime: Date,
  updateTime: Date)

