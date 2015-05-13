/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc

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
    val Unsupported = Value
    val Gentrap = Value
  }

  /** Allowed pipeline parameters in HTTP requests */
  val AllowedPipelineParams = Pipeline.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported library types */
  object LibType extends Enumeration {
    type LibType = Value
    val Single = Value
    val Paired = Value
  }

  /** Allowed library type parameters in HTTP requests */
  val AllowedLibTypeParams = LibType.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported statistics accumulation level */
  object AccLevel extends Enumeration {
    type AccLevel = Value
    val Lib = Value
    val Sample = Value
  }

  /** Allowed accumulation level parameters in HTTP requests */
  val AllowedAccLevelParams = AccLevel.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported QC step for sequences */
  object SeqQcPhase extends Enumeration {
    type SeqQcPhase = Value
    val Raw = Value
    val Processed = Value
  }

  /** Allowed sequencing QC step parameters in HTTP requests */
  val AllowedSeqQcPhaseParams = SeqQcPhase.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap
}
