package nl.lumc.sasc.sentinel

import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import nl.lumc.sasc.sentinel.utils.getResourceStream

trait JsonLoader {
  def loadJson(url: String): JValue = parse(getResourceStream(url))
}
