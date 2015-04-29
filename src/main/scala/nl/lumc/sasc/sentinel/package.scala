/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc

/** General package-level information */
package object sentinel {

  /** Supported schema versions */
  object SchemaVersion extends Enumeration {
    type SchemaVersion = Value
    val V04 = Value
  }

  /** Current schema versions */
  val CurrentSchemaVersion = SchemaVersion.V04

  /** Supported pipeline summary schemas */
  object Pipeline extends Enumeration {
    type Pipeline = Value
    val Gentrap = Value
  }

  /** Allowed pipeline parameters in HTTP requests */
  val AllowedPipelineParams = Pipeline.values.map(_.toString.toLowerCase)

  /** Current API version */
  val CurrentApiVersion = "0.1.0"
}
