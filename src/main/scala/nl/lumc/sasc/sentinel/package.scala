/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc

import nl.lumc.sasc.sentinel.db._

/** General package-level information */
package object sentinel {

  /** Supported run summaries */
  object RunSummary extends Enumeration {
    type RunSummary = Value
    val GentrapV04 = Value
  }

  /** Current API version */
  val CurrentApiVersion = "0.1.0"

  /** Supported pipeline summary schemas */
  object Pipeline extends Enumeration {
    type Pipeline = Value
    val Unknown = Value
    val Gentrap = Value
  }

  /** Allowed pipeline parameters in HTTP requests */
  val AllowedPipelineParams = Pipeline.values.map(_.toString.toLowerCase)

  /** Supported library types */
  object LibType extends Enumeration {
    type LibType = Value
    val Single = Value
    val Paired = Value
  }

  /** Allowed library type parameters in HTTP requests */
  val AllowedLibTypeParams = LibType.values.map(_.toString.toLowerCase)

  /** Supported statistics accumulation level */
  object AccLevel extends Enumeration {
    type AccLevel = Value
    val Lib = Value
    val Sample = Value
  }

  /** Allowed accumulation level parameters in HTTP requests */
  val AllowedAccLevelParams = AccLevel.values.map(_.toString.toLowerCase)
}
