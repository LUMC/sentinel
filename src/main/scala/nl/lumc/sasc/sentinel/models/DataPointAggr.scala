package nl.lumc.sasc.sentinel.models

case class DataPointAggr(
  nDataPoints: Option[Long],
  avg: Option[Double],
  max: Option[Double],
  median: Option[Double],
  min: Option[Double],
  stdev: Option[Double])
