package nl.lumc.sasc.sentinel.models

/**
 * Values of an aggregated data point.
 *
 * @param nDataPoints The number of data points aggregated.
 * @param avg Average value of the data points.
 * @param max Maximum value among the data points.
 * @param median Median value of the data points.
 * @param min Minimum value among the data points.
 * @param stdev Standard deviation among the data points.
 */
case class DataPointAggr(
  nDataPoints: Option[Long],
  avg: Option[Double],
  max: Option[Double],
  median: Option[Double],
  min: Option[Double],
  stdev: Option[Double])
