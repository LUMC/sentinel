package nl.lumc.sasc.sentinel.models

abstract class BaseFileDocument {

  def path: String

  def md5: String
}
