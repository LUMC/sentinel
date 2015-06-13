package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Key

case class PipelineCounts(
  @Key("_id") name: String,
  nRuns: Int,
  nSamples: Int,
  nLibs: Int)
