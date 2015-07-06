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
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models.{ SeqStatsAggr, User }

/**
 * Base class that provides support for querying and aggregating statistics for a pipeline.
 */
abstract class StatsProcessor(protected val mongo: MongodbAccessObject) extends Processor {

  // TODO: refactor functions in here ~ we can do with less duplication

  /** Name of the unit attribute that denotes whether it comes from a paired-end library or not. */
  protected implicit val pairAttrib = "isPaired"

  /** MongoDB samples collection name of the pipeline. */
  protected lazy val samplesColl = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** MongoDB libraries collection name of the pipeline. */
  protected lazy val libsColl = mongo.db(collectionNames.pipelineLibs(pipelineName))

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
  private[processors] def mapFunc(metricName: String,
                                  libType: Option[LibType.Value])(implicit pairAttrib: String): JSFunction = {

    val isPaired = libType match {
      case None                 => "undefined" // don't check for pairs
      case Some(LibType.Paired) => "true" // check for isPaired === true
      case Some(LibType.Single) => "false" // check for isPaired === false
      case otherwise            => throw new NotImplementedError
    }

    // nestedAttrCheck adapted from http://stackoverflow.com/a/2631521/243058
    s"""function map() {
    |
    |     // Given an object and a string denoting its property (nested with arbitrary depth), return whether
    |     // the property exists.
    |     function nestedAttrCheck(o, s) {
    |       s = s.split('.');
    |       var obj = o[s.shift()];
    |       while (obj && s.length) obj = obj[s.shift()];
    |       return (obj !== undefined);
    |     }
    |
    |     var hasMetric = nestedAttrCheck(this, '$metricName');
    |
    |     // Checks whether the library is paired. Ignored if expected value is undefined.
    |     if ($isPaired !== undefined) {
    |       hasMetric = hasMetric && (this.$pairAttrib === $isPaired);
    |     }
    |
    |     if (hasMetric) {
    |       emit("$metricName",
    |         {
    |           sum: this.$metricName,
    |           min: this.$metricName,
    |           max: this.$metricName,
    |           arr: [this.$metricName],
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
   * @param libType Library type of the retrieved statistics. If not specified, all library types are used.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param runs Run IDs of the returned statistics. If not specified, unit statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, unit statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, unit statistics are not
   *                    filtered by annotation IDs.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @tparam T Case class representing the metrics object to return.
   * @return Sequence of unit statistics objects.
   */
  // format: OFF
  def getStatsByAcc[T <: AnyRef](metricName: String)
                                (accLevel: AccLevel.Value,
                                 libType: Option[LibType.Value] = None,
                                 user: Option[User] = None,
                                 runs: Seq[ObjectId] = Seq(),
                                 references: Seq[ObjectId] = Seq(),
                                 annotations: Seq[ObjectId] = Seq(),
                                 timeSorted: Boolean = false)
                                (implicit m: Manifest[T]): Seq[T] = {
    // format: ON

    // Match operation to filter for run, reference, and/or annotation IDs
    val opMatchFilters = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired))

    // Projection for data point label
    val labelProjection =
      MongoDBObject(
        "runId" -> "$runId",
        "runName" -> "$runName",
        "sampleName" -> "$sampleName") ++ {
          if (accLevel == AccLevel.Lib) MongoDBObject("libName" -> "$libName")
          else MongoDBObject.empty
        }

    // Collection to query on
    val coll = accLevel match {
      case AccLevel.Sample => samplesColl
      case AccLevel.Lib    => libsColl
      case otherwise       => throw new NotImplementedError
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

      timeSorted match {
        case true  => Seq(opMatchFilters, opSortUnit, opProjectAlnStats)
        case false => Seq(opMatchFilters, opProjectAlnStats)
      }
    }

    lazy val results = coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map {
        case aggres =>
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
          dbo.collect {
            case obj => grater[T].asObject(obj)
          }
      }.toSeq.flatten

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves sequence statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param libType Library type of the returned sequence statistics.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param runs Run IDs of the returned statistics. If not specified, sequence statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, sequence statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, sequence statistics are not
   *                    filtered by annotation IDs.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @tparam T Case class representing the metrics object to return.
   * @return Sequence of sequence statistics objects.
   */
  // format: OFF
  def getLibStats[T <: AnyRef](metricName: String)
                              (libType: Option[LibType.Value],
                               user: Option[User],
                               runs: Seq[ObjectId] = Seq(),
                               references: Seq[ObjectId] = Seq(),
                               annotations: Seq[ObjectId] = Seq(),
                               timeSorted: Boolean = false)
                              (implicit m: Manifest[T]): Seq[T] = {
    // format: ON

    // Match operation to filter for run, reference, annotation IDs, and/or library type
    val opMatchFilters = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired))

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
            "libName" -> "$libName")))
    }

    val operations = timeSorted match {
      case true  => Seq(opMatchFilters, opSortUnit, opProjectStats)
      case false => Seq(opMatchFilters, opProjectStats)
    }

    lazy val results = libsColl
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map {
        case aggres =>
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
          dbo.collect {
            case obj => grater[T].asObject(obj)
          }
      }.toSeq.flatten

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves aggregated unit statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param metricAttrNames Sequence of names of the metric container object attribute to aggregate on.
   * @param accLevel Accumulation level of the retrieved statistics.
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
  def getAggrStatsByAcc[T <: AnyRef](metricName: String,
                                     metricAttrNames: Seq[String])
                                    (accLevel: AccLevel.Value,
                                     libType: Option[LibType.Value],
                                     runs: Seq[ObjectId] = Seq(),
                                     references: Seq[ObjectId] = Seq(),
                                     annotations: Seq[ObjectId] = Seq())
                                    (implicit m: Manifest[T]): Option[T] = {
    // format: ON

    val query = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired), withKey = false)

    val coll = accLevel match {
      case AccLevel.Sample => samplesColl
      case AccLevel.Lib    => libsColl
      case otherwise       => throw new NotImplementedError
    }

    def mapRecResults(attr: String) = coll
      .mapReduce(
        mapFunction = mapFunc(s"$metricName.$attr", libType),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(query))
      .toSeq
      .headOption
      .collect {
        case res =>
          MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty))
      }

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
   * @param metricAttrNames Sequence of names of the metric container object attribute to aggregate on.
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
  def getSeqAggregateStats[T <: AnyRef](metricName: String,
                                        metricAttrNames: Seq[String])
                                       (libType: Option[LibType.Value],
                                        runs: Seq[ObjectId] = Seq(),
                                        references: Seq[ObjectId] = Seq(),
                                        annotations: Seq[ObjectId] = Seq())
                                       (implicit m: Manifest[T]): Option[SeqStatsAggr[T]] = {
    // format: ON

    // Query for selecting documents pre-mapReduce
    val query = buildMatchOp(runs, references, annotations, libType.map(_ == LibType.Paired), withKey = false)

    def mapRecResults(attr: String, readName: String) = libsColl
      .mapReduce(
        mapFunction = mapFunc(s"$metricName.$readName.$attr", libType),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(query))
      .toSeq
      .headOption
      .collect { case res => MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)) }

    val readNames = libType match {
      case Some(LibType.Single) => Seq("read1")
      case otherwise            => Seq("read1", "read2", "readAll")
    }

    val aggrStats = readNames.par
      .map {
        case rn =>
          val res = metricAttrNames.par
            .flatMap { case an => mapRecResults(an, rn) }
            .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }
          if (res.nonEmpty) (rn, Option(grater[T].asObject(res)))
          else (rn, None)
      }.seq
      .filter { case (rn, res) => res.isDefined }
      .map { case (rn, res) => (rn, res.get) }
      .toMap

    aggrStats.get("read1").collect {
      case r1 => SeqStatsAggr(read1 = r1, read2 = aggrStats.get("read2"), readAll = aggrStats.get("readAll"))
    }
  }
}
