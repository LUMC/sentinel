package nl.lumc.sasc.sentinel

import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import nl.lumc.sasc.sentinel.utils.getResourceFile

trait JsonLoader {
  def loadJson(url: String): JValue = parse(getResourceFile(url))
}
