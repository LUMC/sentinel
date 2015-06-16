package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

/** Representation of a library within a sample. */
@Salat abstract class BaseLibDocument {

  /** Name of the run that produced this library. */
  def runName: Option[String]

  /** Name of the sample which this library belongs to. */
  def sampleName: Option[String]

  /** Library name. */
  def libName: Option[String]
}
