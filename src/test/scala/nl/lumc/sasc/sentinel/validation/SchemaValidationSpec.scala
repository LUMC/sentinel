package nl.lumc.sasc.sentinel.validation

import org.json4s.jackson.JsonMethods.parse
import org.json4s.JValue
import org.specs2._

import nl.lumc.sasc.sentinel.utils.getResourceFile

trait SchemaValidationSpec { this: Specification =>

  def loadJson(url: String): JValue = parse(getResourceFile(url))
}
