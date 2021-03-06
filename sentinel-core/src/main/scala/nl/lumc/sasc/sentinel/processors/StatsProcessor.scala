/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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

import scala.concurrent._
import scala.language.higherKinds
import scala.reflect.runtime.{universe => ru}
import scala.util.Random.shuffle
import scala.util.Try

import com.novus.salat.{CaseClass => _, _}
import com.novus.salat.global.{ctx => SalatContext}
import com.mongodb.casbah.Imports._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.{extractFieldNames, FutureMixin, MongodbAccessObject}
import nl.lumc.sasc.sentinel.utils.reflect.getReadStatsManifest

/**
 * Base class that provides support for querying and aggregating statistics for a pipeline.
 */
abstract class StatsProcessor(protected[processors] val mongo: MongodbAccessObject) extends SingularProcessor {

  /** Overridable execution context for this processor. */
  protected def statsProcessorContext = ExecutionContext.global

  /** Execution context for Future operations. */
  implicit private def context: ExecutionContext = statsProcessorContext

  /** Helper method to get the collection to query on based on a given accLevel parameter. */
  private def getColl(accLevel: AccLevel.Value): Perhaps[MongoCollection] = accLevel match {
    case AccLevel.Sample    => samplesColl.right
    case AccLevel.ReadGroup => readGroupsColl.right
    // Safeguard for cases when we forgot to extend the case here after defining a new enum for AccLevel.
    // This is because Scala's enum case check is done at runtime and not compile time.
    case otherwise => Payloads.UnexpectedError
      .copy(hints = List(s"Unexpected accumulation level parameter ${otherwise.toString}"))
      .left
  }

  /** MongoDB samples collection name of the pipeline. */
  protected[processors] lazy val samplesColl = mongo.db(collectionNames.pipelineSamples(pipelineName))

  /** MongoDB read groups collection name of the pipeline. */
  protected[processors] lazy val readGroupsColl = mongo.db(collectionNames.pipelineReadGroups(pipelineName))

  /** Sort operation for unit documents */
  protected[processors] val opSortUnit = MongoDBObject("$sort" -> MongoDBObject("creationTimeUtc" -> -1))

  /** Default mapReduce MongoDB output for this processor. */
  protected[processors] def mapReduceOutput: MapReduceOutputTarget = MapReduceInlineOutput

  /**
   * Runs a map reduce query.
   *
   * @param coll MongoDB collection to run map reduce on.
   * @param query MongoDB initial query for selecting documents for map reduce.
   * @param attrIndexNames Names of attribute to perform map reduce on.
   * @return MapReduce results.
   */
  protected[processors] def runMapReduce(coll: MongoCollection)(query: Option[DBObject])(attrIndexNames: Seq[String]) = coll
    .mapReduce(
      mapFunction = mapFunc(attrIndexNames),
      reduceFunction = reduceFunc,
      output = mapReduceOutput,
      finalizeFunction = Option(finalizeFunc),
      query = query
    )
    .toSeq
    .headOption
    .map { res =>
      val resValue = res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)
      MongoDBObject(attrIndexNames.last -> adjustMapReduceLongs(resValue))
    }

  /** Raw string of the map function for mapReduce. */
  protected[processors] final def mapFunc(metricNames: Seq[String]): JSFunction = {

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
  protected[processors] final val reduceFunc =
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
  protected[processors] final val finalizeFunc =
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
   * @param nLimit Maximum number of data points to return. If set to `None`, all data points will be returned.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @tparam T Labeled stats object representing the metrics object to return.
   * @return Sequence of unit statistics objects.
   */
  // format: OFF
  def getStats[T <: LabeledStats](metricName: String)
                                 (accLevel: AccLevel.Value)
                                 (matchers: MongoDBObject,
                                  user: Option[User] = None,
                                  nLimit: Option[Int]= None,
                                  timeSorted: Boolean = false)
                                 (implicit m: Manifest[T]): Future[Perhaps[Seq[T]]] = {
    // format: ON

    // MongoDB aggregation framework operations
    val operations = {
      val opProjectAlnStats =
        MongoDBObject("$project" ->
          MongoDBObject(
            "_id" -> 0,
            metricName -> 1,
            "uploaderId" -> 1,
            "runId" -> 1,
            "labels" -> 1
          ))

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

    val statsGrater = grater[T]

    // Helper method for running the query
    def runQuery(coll: MongoCollection) = Future {
      coll
        .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
        .map { aggres =>
          val uploaderId = aggres.getAs[String]("uploaderId")
          val astat = aggres.getAs[DBObject](metricName)
          val labels = aggres.getAs[MongoDBObject]("labels")
          val runId = aggres.getAs[ObjectId]("runId")
          val dbo = (user, uploaderId, astat, labels) match {
            case (Some(u), Some(uid), Some(s), Some(n)) =>
              if (u.id == uid) Option(s ++
                MongoDBObject("labels" -> (n ++ MongoDBObject("runId" -> runId))))
              else Option(s)
            case (None, _, Some(s), _) => Option(s)
            case otherwise             => None
          }
          dbo.map { obj => statsGrater.asObject(obj) }
        }.toSeq.flatten
    }

    val results = for {
      coll <- ? <~ getColl(accLevel)
      queryResults <- ? <~ runQuery(coll)
      items = if (timeSorted) queryResults else shuffle(queryResults)
      // TODO: switch to database-level randomization when SERVER-533 is resolved
    } yield nLimit match {
      case None    => items
      case Some(n) => items.take(n)
    }

    results.run
  }

  /**
   * Retrieves aggregated unit statistics.
   *
   * @param metricName Name of the main metrics container object in the unit.
   * @param accLevel Accumulation level of the retrieved statistics.
   * @param matchers MongoDBObject containing query parameters.
   * @param libType Library type of the returned statistics.
   * @tparam T Case class representing the aggregated metrics object to return.
   * @return Unit statistics aggregates.
   */
  // format: OFF
  def getAggregateStats[T <: CaseClass](metricName: String)
                                       (accLevel: AccLevel.Value)
                                       (libType: Option[LibType.Value])
                                       (matchers: MongoDBObject)
                                       (implicit m: Manifest[T], tt: ru.TypeTag[T]): Future[Perhaps[T]] = {
    // format: ON

    // Helper method to run the query
    def runQuery(coll: MongoCollection) = Future {
      val mapReduceFunc = runMapReduce(coll)(Option(matchers)) _

      if (!(tt.tpe <:< ru.weakTypeTag[FragmentStatsAggrLike[_]].tpe)) {

        val aggrStats = extractFieldNames[T].par
          .flatMap { n => mapReduceFunc(Seq(metricName, n)) }
          .foldLeft(MongoDBObject.empty)(_ ++ _)

        aggrStats.nonEmpty
          .option { grater[T].asObject(aggrStats) }
          .toRightDisjunction(Payloads.DataPointsNotFoundError)

      } else {

        // Manifest of the inner type of FragmentStatsLike
        val readStatsManif = getReadStatsManifest[T]
        val seqGrater = grater(SalatContext, readStatsManif)
        val metricAttrNames = extractFieldNames(readStatsManif).toSeq
        val readNames = libType match {
          case Some(LibType.Single) => Seq(FragmentStatsLike.singleReadAttr)
          case otherwise            => FragmentStatsLike.readAttrs
        }

        // Process inner read statistics first
        val innerStats = readNames.par
          .flatMap { rn =>
            val res = metricAttrNames.par
              .flatMap { an => mapReduceFunc(Seq(metricName, rn, an)) }
              .foldLeft(MongoDBObject.empty)(_ ++ _)
            res.nonEmpty
              .option { MongoDBObject(rn -> seqGrater.asObject(res)) }
          }
          .foldLeft(MongoDBObject.empty)(_ ++ _)

        // Then process outer fragment statistics
        val outerStats = extractFieldNames(m).par
          .filterNot { FragmentStatsLike.readAttrs.contains }
          .flatMap { n => mapReduceFunc(Seq(metricName, n)) }
          .foldLeft(MongoDBObject.empty)(_ ++ _)

        val aggrStats = innerStats ++ outerStats
        aggrStats
          .contains(FragmentStatsLike.singleReadAttr)
          .option { grater[T].asObject(aggrStats) }
          .toRightDisjunction(Payloads.DataPointsNotFoundError)
      }
    }

    val results = for {
      coll <- ? <~ getColl(accLevel)
      queryResults <- ? <~ runQuery(coll)
    } yield queryResults

    results.run
  }

  // NOTE: Java's MongoDB driver parses all MapReduce number results to Double, so we have to resort to this.
  /** Transforms MongoDB mapReduce nDataPoints attribute to the proper type */
  private def adjustMapReduceLongs(dbo: DBObject): DBObject = {

    dbo.keys.foreach { k =>
      dbo(k) = dbo(k) match {

        case innerDbo: DBObject =>

          val value = for {
            v1 <- Option(innerDbo.get("nDataPoints"))
            v2 <- Try(v1.toString.toDouble.toLong).toOption
          } yield v2
          value.foreach { v => innerDbo("nDataPoints") = Long.box(v) }
          innerDbo

        case otherwise => otherwise
      }
    }

    dbo
  }
}
