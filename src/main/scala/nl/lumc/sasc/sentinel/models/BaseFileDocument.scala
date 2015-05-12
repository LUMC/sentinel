package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseFileDocument {

  def path: String

  def md5: String
}
