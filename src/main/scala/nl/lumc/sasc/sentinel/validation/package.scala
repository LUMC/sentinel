/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel

import nl.lumc.sasc.sentinel.utils.getResourceFile

package object validation {

  /** Helper function to fetch schemas. */
  private[this] def getSchema(schemaUrl: String) = getResourceFile("/schemas/" + schemaUrl)

  /** Mapping of supported schema version, the pipeline schema, and its actual validator. */
  val Schemas = Map(
    SchemaVersion.V04 ->
      Map(Pipeline.Gentrap -> new IncomingValidator(getSchema("v0.4/gentrap.json")))
  )
}
