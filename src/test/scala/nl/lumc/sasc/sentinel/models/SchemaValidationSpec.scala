package nl.lumc.sasc.sentinel.models

import org.json4s.jackson.JsonMethods.parse
import org.json4s.JValue
import org.specs2._

import nl.lumc.sasc.sentinel.db.DatabaseProvider
import nl.lumc.sasc.sentinel.processors.RunProcessor
import nl.lumc.sasc.sentinel.utils.getResourceFile

trait SchemaValidationSpec { this: Specification =>

  trait MockDatabaseProvider extends DatabaseProvider { this: RunProcessor => }

  def loadJson(url: String): JValue = parse(getResourceFile(url))
}
