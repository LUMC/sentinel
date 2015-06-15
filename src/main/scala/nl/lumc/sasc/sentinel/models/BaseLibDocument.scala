package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseLibDocument {

  def runName: Option[String]

  def sampleName: Option[String]

  def libName: Option[String]
}
