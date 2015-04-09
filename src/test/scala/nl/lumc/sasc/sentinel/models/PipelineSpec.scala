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
import nl.lumc.sasc.sentinel.validation.{ IncomingValidator, Schemas }

/** Trait for pipeline summary tests; must extend the specs2 [[Specification]]. */
trait PipelineSpec { this: Specification =>

  /** Pipeline schema to test against */
  val pipeline: Pipeline.Value

  /** Schema version to test against */
  val schemaVersion: SchemaVersion.Value

  /** Schema object to test against */
  def schema: IncomingValidator = Schemas(schemaVersion)(pipeline)

  /** Loads the given test resource as a [[JValue]] object. */
  def loadResource(url: String): JValue = parse(getResourceFile(url))
}
