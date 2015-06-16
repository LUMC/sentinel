package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Key

/**
 * Container for per-pipeline statistics.
 *
 * @param name Pipeline name.
 * @param nRuns Total number of runs of the pipeline.
 * @param nSamples Total number of samples from all runs of the pipeline.
 * @param nLibs Total number of libraries from all runs of the pipeline.
 */
case class PipelineStats(
  @Key("_id") name: String,
  nRuns: Int,
  nSamples: Int,
  nLibs: Int)
