/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 *
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc

/** General package-level information */
package object sentinel {

  /** Current API version */
  val CurrentApiVersion = "0.1.0"

  /** Header API key */
  val HeaderApiKey = "X-SENTINEL-KEY"

  object settings {

    val MaxRunSummarySizeMb = 16
    val MaxRunSummarySize = MaxRunSummarySizeMb * 1024 * 1024

    val MinUserIdLength = 3

    val MinPasswordLength = 6
  }

  /** Supported pipeline summary schemas */
  object Pipeline extends Enumeration {
    type Pipeline = Value
    val Unsupported = Value("unsupported")
    val Gentrap = Value("gentrap")
  }

  /** Allowed pipeline parameters in HTTP requests */
  val AllowedPipelineParams = Pipeline.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported aggregation parameters */
  object AggrStat extends Enumeration {
    type AggrStat = Value
    val Sum = Value("sum")
    val Min = Value("min")
    val Max = Value("max")
    val Mean = Value("mean")
    val Median = Value("median")
    val Variance = Value("variance")
    val Stdev = Value("stdev")
  }

  /** Allowed aggregation parameters in HTTP request */
  val AllowedAggrStatParams = AggrStat.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported library types */
  object LibType extends Enumeration {
    type LibType = Value
    val Single = Value("single")
    val Paired = Value("paired")
  }

  /** Allowed library type parameters in HTTP requests */
  val AllowedLibTypeParams = LibType.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported statistics accumulation level */
  object AccLevel extends Enumeration {
    type AccLevel = Value
    val Lib = Value("lib")
    val Sample = Value("sample")
  }

  /** Allowed accumulation level parameters in HTTP requests */
  val AllowedAccLevelParams = AccLevel.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap

  /** Supported QC step for sequences */
  object SeqQcPhase extends Enumeration {
    type SeqQcPhase = Value
    val Raw = Value("raw")
    val Processed = Value("processed")
  }

  /** Allowed sequencing QC step parameters in HTTP requests */
  val AllowedSeqQcPhaseParams = SeqQcPhase.values
    .map(enum => enum.toString.toLowerCase -> enum)
    .toMap
}
