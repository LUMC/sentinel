package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseLibDocument {

  def name: Option[String]
}
