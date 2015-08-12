/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.processors

import scala.collection.mutable.ListBuffer
import scala.util.Random.shuffle

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._

import nl.lumc.sasc.sentinel.{ AccLevel, LibType }
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models.{ SeqStatsAggr, User }
import nl.lumc.sasc.sentinel.utils.extractFieldNames

/**
 * Base class that provides support for querying and aggregating statistics for a pipeline.
 */
abstract class StatsProcessor(protected val mongo: MongodbAccessObject) extends Processor {

  // TODO: refactor functions in here ~ we can do with less duplication

  /** Name of the unit attribute that denotes whether it comes from a paired-end library or not. */
  implicit val pairAttrib = StatsProcessor.pairAttrib

  /** MongoDB samples collection name of the pipeline. */
  protected lazy val samplesColl = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** MongoDB read groups collection name of the pipeline. */
  protected lazy val readGroupsColl = mongo.db(collectionNames.pipelineReadGroups(pipelineName))

  /**
   * Match operation builder for collection aggregations.
   *
   * @param runs Run IDs to filter in. If empty, no run ID filtering is done.
   * @param references Reference IDs to filter in. If empty, no reference ID filtering is done.
   * @param annotations Annotation IDs to filter in. If empty, no annotation ID filtering is done.
   * @param paired If defined, a boolean denoting whether the unit is paired-end or not. If not defined, both paired-end
   *               and single-end are included in the match.
   * @param withKey Whether to return only the `query` object with the `$match` key or not.
   * @return a [[DBObject]] representing the `$match` aggregation operation.
   */
  private[processors] def buildMatchOp(runs: Seq[ObjectId], references: Seq[ObjectId], annotations: Seq[ObjectId],
                                       paired: Option[Boolean] = None, withKey: Boolean = true): DBObject = {
    val matchBuffer = new ListBuffer[MongoDBObject]()

    if (runs.nonEmpty)
      matchBuffer += MongoDBObject("runId" -> MongoDBObject("$in" -> runs))

    if (references.nonEmpty)
      matchBuffer += MongoDBObject("referenceId" -> MongoDBObject("$in" -> references))

    if (annotations.nonEmpty)
      matchBuffer += MongoDBObject("annotationIds" ->
        MongoDBObject("$elemMatch" -> MongoDBObject("$in" -> annotations)))

    paired match {
      case Some(isPaired) => matchBuffer += MongoDBObject(pairAttrib -> isPaired)
      case None           => ;
    }

    val query =
      if (matchBuffer.nonEmpty) MongoDBObject("$and" -> matchBuffer.toSeq)
      else MongoDBObject.empty

    if (withKey) MongoDBObject("$match" -> query)
    else query
  }

  /** Sort operation for unit documents */
  private[processors] val opSortUnit = MongoDBObject("$sort" -> MongoDBObject("creationTimeUtc" -> -1))

  /** Raw string of the map function for mapReduce. */
  private[processors] def mapFunc(metricNames: String*)(implicit pairAttrib: String): JSFunction = {

    val metricName = metricNames.mkString(".")
    val metricsArrayJs = "[ '" + metricNames.mkString("', '") + "' ]"

    // getNestedValue adapted from http://stackoverflow.com/a/2631521/243058
    s"""function map() {
    |
    |     var attrArray = $metricsArrayJs;
    |     var res = this[attrArray.shift()];
    |     while (res && attrArray.length) res = res[attrArray.shift()];
    |     var nestedValue = res;
    |
    |     if (nestedValue !== undefined) {
    |       emit("$metricName",
    |         {
    |           sum: nestedValue,
    |           min: nestedValue,
    |           max: nestedValue,
    |           arr: [nestedValue],
    |           nDataPoints: 1,
    |           diff: 0
    |         });
    |     }
    |   }
    """.stripMargin
  }

  /** Reduce runction for mapReduce */
  private[processors] val reduceFunc =
    """function reduce(key, values) {
      |
      |  var a = values[0];
      |
      |  for (var i = 1; i < values.length; i++){
      |    var b = values[i];
      |
      |    // temp helpers
      |    var delta = a.sum / a.nDataPoints - b.sum / b.nDataPoints;
      |    var weight = (a.nDataPoints * b.nDataPoints) / (a.nDataPoints + b.nDataPoints);
      |
      |    // do the reducing
      |    a.diff += b.diff + delta * delta * weight;
      |    a.sum += b.sum;
      |    a.nDataPoints += b.nDataPoints;
      |    a.arr = a.arr.concat(b.arr);
      |    a.min = Math.min(a.min, b.min);
      |    a.max = Math.max(a.max, b.max);
      |  }
      |
      |  return a;
      |}
    """.stripMargin

  /** Finalize function for mapReduce */
  private[processors] val finalizeFunc =
    """function finalize(key, value) {
      |
      |  value.avg = value.sum / value.nDataPoints;
      |  value.variance = value.diff / value.nDataPoints;
      |  value.stdev = Math.sqrt(value.variance);
      |
      |  value.arr.sort();
      |  if (value.arr.length % 2 === 0) {
      |      var half = value.arr.length / 2;
      |      value.median = (value.arr[half - 1] + value.arr[half]) / 2;
      |  } else {
      |      value.median = value.arr[(value.arr.length - 1) / 2];
      |  }
      |  delete value.arr;
      |
      |  return value;
      |}
    """.stripMargin

  /**
   * Retrieves unit statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param accLevel Accumulation level of the retrieved statistics.
   * @param matchers MongoDBObject containing query parameters.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @tparam T Case class representing the metrics object to return.
   * @return Sequence of unit statistics objects.
   */
  // format: OFF
  def getStatsByAcc[T <: AnyRef](metricName: String)
                                (accLevel: AccLevel.Value)
                                (matchers: MongoDBObject,
                                 user: Option[User] = None,
                                 timeSorted: Boolean = false)
                                (implicit m: Manifest[T]): Seq[T] = {
    // format: ON

    // Projection for data point label
    val labelProjection =
      MongoDBObject(
        "runId" -> "$runId",
        "runName" -> "$runName",
        "sampleName" -> "$sampleName") ++ {
          if (accLevel == AccLevel.ReadGroup) MongoDBObject("readGroupName" -> "$readGroupName")
          else MongoDBObject.empty
        }

    // MongoDB aggregation framework operations
    val operations = {
      val opProjectAlnStats =
        MongoDBObject("$project" ->
          MongoDBObject(
            "_id" -> 0,
            metricName -> 1,
            "uploaderId" -> 1,
            "labels" -> labelProjection))

      // Initial document selection
      val opMatch = MongoDBObject("$match" -> matchers).asDBObject

      timeSorted match {
        case true =>
          if (matchers.isEmpty) Seq(opSortUnit, opProjectAlnStats)
          else Seq(opMatch, opSortUnit, opProjectAlnStats)
        case false =>
          if (matchers.isEmpty) Seq(opProjectAlnStats)
          else Seq(opMatch, opProjectAlnStats)
      }
    }

    // Collection to query on
    val coll = accLevel match {
      case AccLevel.Sample    => samplesColl
      case AccLevel.ReadGroup => readGroupsColl
      case otherwise          => throw new NotImplementedError
    }

    lazy val results = coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map { aggres =>
        val uploaderId = aggres.getAs[String]("uploaderId")
        val labels = aggres.getAs[DBObject]("labels")
        val astat = aggres.getAs[DBObject](metricName)
        val dbo = (user, uploaderId, astat, labels) match {
          case (Some(u), Some(uid), Some(s), Some(n)) =>
            if (u.id == uid) Option(s ++ MongoDBObject("labels" -> n))
            else Option(s)
          case (None, _, Some(s), _) => Option(s)
          case otherwise             => None
        }
        dbo.map { obj => grater[T].asObject(obj) }
      }.toSeq.flatten

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves read group statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param matchers MongoDBObject containing query parameters.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @tparam T Case class representing the metrics object to return.
   * @return Sequence of sequence statistics objects.
   */
  // format: OFF
  def getReadGroupStats[T <: AnyRef](metricName: String)
                                    (matchers: MongoDBObject,
                                     user: Option[User] = None,
                                     timeSorted: Boolean = false)
                                    (implicit m: Manifest[T]): Seq[T] = {
    // format: ON

    val operations = {

      // Projection operation for retrieving innermost stats object
      val opProjectStats = {

        MongoDBObject("$project" ->
          MongoDBObject(
            "_id" -> 0,
            "uploaderId" -> "$uploaderId",
            metricName -> 1,
            "labels" -> MongoDBObject(
              "runId" -> "$runId",
              "runName" -> "$runName",
              "sampleName" -> "$sampleName",
              "readGroupName" -> "$readGroupName")))
      }

      // Initial document selection
      val opMatch = MongoDBObject("$match" -> matchers).asDBObject

      timeSorted match {
        case true  => Seq(opMatch, opSortUnit, opProjectStats)
        case false => Seq(opMatch, opProjectStats)
      }
    }

    lazy val results = readGroupsColl
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map { aggres =>
        val uploaderId = aggres.getAs[String]("uploaderId")
        val labels = aggres.getAs[DBObject]("labels")
        val astat = aggres.getAs[DBObject](metricName)
        val dbo = (user, uploaderId, astat, labels) match {
          case (Some(u), Some(uid), Some(s), Some(n)) =>
            if (u.id == uid) Option(s ++ MongoDBObject("labels" -> n))
            else Option(s)
          case (None, _, Some(s), _) => Option(s)
          case otherwise             => None
        }
        dbo.map { obj => grater[T].asObject(obj) }
      }.toSeq.flatten

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves aggregated unit statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param accLevel Accumulation level of the retrieved statistics.
   * @param matchers MongoDBObject containing query parameters.
   * @tparam T Case class representing the aggregated metrics object to return.
   * @return Alignment statistics aggregates.
   */
  // format: OFF
  def getAggrStatsByAcc[T <: AnyRef](metricName: String)
                                    (accLevel: AccLevel.Value,
                                     matchers: MongoDBObject)
                                    (implicit m: Manifest[T]): Option[T] = {
    // format: ON

    val coll = accLevel match {
      case AccLevel.Sample    => samplesColl
      case AccLevel.ReadGroup => readGroupsColl
      case otherwise          => throw new NotImplementedError
    }

    def mapRecResults(attr: String) = coll
      .mapReduce(
        mapFunction = mapFunc(metricName, attr),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(matchers))
      .toSeq
      .headOption
      .map { res => MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)) }

    val aggrStats = extractFieldNames[T].par
      .flatMap { mapRecResults }
      .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }

    if (aggrStats.isEmpty) None
    else Option(grater[T].asObject(aggrStats))
  }

  /**
   * Retrieves aggregated read group statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param metricAttrNames Sequence of names of the metric container object attribute to aggregate on.
   * @param libType Library type of the retrieved statistics. If not specified, all library types are used.
   * @param runs Run IDs of the returned statistics. If not specified, unit statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, unit statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, unit statistics are not
   *                    filtered by annotation IDs.
   * @tparam T Case class representing the aggregated metrics object to return.
   * @return Alignment statistics aggregates.
   */
  // format: OFF
  def getReadGroupAggrStats[T <: AnyRef](metricName: String,
                                         metricAttrNames: Seq[String])
                                        (libType: Option[LibType.Value],
                                         runs: Seq[ObjectId] = Seq.empty,
                                         references: Seq[ObjectId] = Seq.empty,
                                         annotations: Seq[ObjectId] = Seq.empty)
                                        (implicit m: Manifest[T]): Option[T] = {
    // format: ON

    val query = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired), withKey = false)

    def mapRecResults(attr: String) = readGroupsColl
      .mapReduce(
        mapFunction = mapFunc(metricName, attr),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(query))
      .toSeq
      .headOption
      .map { res => MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)) }

    val aggrStats = metricAttrNames.par
      .flatMap { mapRecResults }
      .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }

    if (aggrStats.isEmpty) None
    else Option(grater[T].asObject(aggrStats))
  }

  /**
   * Retrieves aggregated sequence statistics.
   *
   * @param metricName Name of the main metrics container object in the sequence.
   * @param libType Library type of the returned sequence statistics.
   * @param runs Run IDs of the returned statistics. If not specified, sequence statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, sequence statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, sequence statistics are not
   *                    filtered by annotation IDs.
   * @tparam T Case class representing the aggregated metrics object to return.
   * @return Sequence statistics aggregates.
   */
  // format: OFF
  def getSeqAggregateStats[T <: AnyRef](metricName: String)
                                       (libType: Option[LibType.Value],
                                        runs: Seq[ObjectId] = Seq.empty,
                                        references: Seq[ObjectId] = Seq.empty,
                                        annotations: Seq[ObjectId] = Seq.empty)
                                       (implicit m: Manifest[T]): Option[SeqStatsAggr[T]] = {
    // format: ON

    // Query for selecting documents pre-mapReduce
    val query = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired), withKey = false)

    def mapRecResults(attr: String, readName: String) = readGroupsColl
      .mapReduce(
        mapFunction = mapFunc(metricName, readName, attr),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(query))
      .toSeq
      .headOption
      .map { res => MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)) }

    val readNames = libType match {
      case Some(LibType.Single) => Seq("read1")
      case otherwise            => Seq("read1", "read2", "readAll")
    }

    val metricAttrNames = extractFieldNames[T].toSeq

    val aggrStats = readNames.par
      .map { rn =>
        val res = metricAttrNames.par
          .flatMap { an => mapRecResults(an, rn) }
          .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }
        if (res.nonEmpty) (rn, Option(grater[T].asObject(res)))
        else (rn, None)
      }.seq
      .flatMap { case (rn, res) => res.map(r => (rn, r)) }
      .toMap

    aggrStats.get("read1").map { r1 =>
      SeqStatsAggr(read1 = r1, read2 = aggrStats.get("read2"), readAll = aggrStats.get("readAll"))
    }
  }
}

object StatsProcessor {

  /** Name of the unit attribute that denotes whether it comes from a paired-end library or not. */
  def pairAttrib = "isPaired"
}
