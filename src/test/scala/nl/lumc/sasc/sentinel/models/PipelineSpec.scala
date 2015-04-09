/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel.models

import org.json4s.jackson.JsonMethods.parse
import org.json4s.JValue
import org.specs2._

import nl.lumc.sasc.sentinel.{ Pipeline, SchemaVersion }
import nl.lumc.sasc.sentinel.utils.getResourceFile
import nl.lumc.sasc.sentinel.validation.Schemas

/** Trait for pipeline summary tests; must extend the specs2 [[Specification]]. */
trait PipelineSpec { this: Specification =>

  val pipeline: Pipeline.Value

  val schemaVersion: SchemaVersion.Value

  def schema = Schemas(schemaVersion)(pipeline)

  def loadResource(url: String): JValue = parse(getResourceFile(url))
}
