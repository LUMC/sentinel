/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel

import java.io.File
import java.nio.file.Paths

/** General utilities */
package object utils {

  def getResourcePath(url: String): String = {
    require(url startsWith "/", "Resource paths must start with '/'")
    Option(getClass.getResource(url)) match {
      case Some(u) => Paths.get(u.toURI).toString
      case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
    }
  }

  def getResourceFile(url: String): File = new File(getResourcePath(url))
}
