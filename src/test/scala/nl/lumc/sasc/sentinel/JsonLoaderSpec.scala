package nl.lumc.sasc.sentinel

import nl.lumc.sasc.sentinel.utils.getResourceFile
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse
import org.specs2._

trait JsonLoaderSpec { this: Specification =>

  def loadJson(url: String): JValue = parse(getResourceFile(url))
}
