package nl.lumc.sasc.sentinel.validation

import org.json4s.jackson.JsonMethods.parse
import org.json4s.JValue
import org.specs2._
import org.specs2.mock.Mockito

import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.utils.getResourceFile

trait SchemaValidationSpec { this: Specification with Mockito =>

  val mockDb = mock[MongodbAccessObject]

  def loadJson(url: String): JValue = parse(getResourceFile(url))
}
