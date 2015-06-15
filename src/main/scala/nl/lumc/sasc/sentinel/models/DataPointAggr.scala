package nl.lumc.sasc.sentinel.models

case class DataPointAggr(
  nDataPoints: Option[Long],
  max: Option[Double],
  mean: Option[Double],
  median: Option[Double],
  min: Option[Double],
  stdev: Option[Double])