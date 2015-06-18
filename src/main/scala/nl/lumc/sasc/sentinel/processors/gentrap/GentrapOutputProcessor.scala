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
package nl.lumc.sasc.sentinel.processors.gentrap

import scala.util.Random.shuffle

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.db.{ MongodbAccessObject, MongodbConnector }
import nl.lumc.sasc.sentinel.models._

import scala.collection.mutable.ListBuffer

/**
 * Output processor for Gentrap endpoints.
 *
 * @param mongo MongoDB database access object.
 */
class GentrapOutputProcessor(protected val mongo: MongodbAccessObject) extends MongodbConnector {

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.pipelineSamples("gentrap"))

  /**
   * Match operation builder for collection aggregations.
   *
   * @param runs Run IDs to filter in. If empty, no run ID filtering is done.
   * @param references Reference IDs to filter in. If empty, no reference ID filtering is done.
   * @param annotations Annotation IDs to filter in. If empty, no annotation ID filtering is done.
   * @param withKey Whether to return only the `query` object with the `$match` key or not.
   * @return a [[DBObject]] representing the `$match` aggregation operation.
   */
  private[processors] def buildMatchOp(runs: Seq[ObjectId], references: Seq[ObjectId],
                                       annotations: Seq[ObjectId], withKey: Boolean = true): DBObject = {
    val matchBuffer = new ListBuffer[MongoDBObject]()

    if (runs.nonEmpty)
      matchBuffer += MongoDBObject("runId" -> MongoDBObject("$in" -> runs))

    if (references.nonEmpty)
      matchBuffer += MongoDBObject("referenceId" -> MongoDBObject("$in" -> references))

    if (annotations.nonEmpty)
      matchBuffer += MongoDBObject("annotationIds" ->
        MongoDBObject("$elemMatch" -> MongoDBObject("$in" -> annotations)))

    val query =
      if (matchBuffer.nonEmpty) MongoDBObject("$and" -> matchBuffer.toSeq)
      else MongoDBObject.empty

    if (withKey) MongoDBObject("$match" -> query)
    else query
  }

  /**
   * Match operation builder for aggregation after unwinding the libs array of a Sample document.
   *
   * The returned aggregation operator for this function is meant to be used only after the libs array of a Sample
   * document has been unwound. This is because the returned operator relies on the 'libs' attribute value not being
   * an array anymore.
   *
   * @param libType Library type to filter in.
   * @return [[DBObject]] representing the `$match` aggregation operation.
   */
  private[processors] def buildMatchPostUnwindOp(libType: Option[LibType.Value]): DBObject = {

    val query = libType match {
      case None                 => MongoDBObject.empty
      case Some(LibType.Paired) => MongoDBObject("libs.seqStatsRaw.read2" -> MongoDBObject("$exists" -> true))
      case Some(LibType.Single) => MongoDBObject("libs.seqStatsRaw.read2" -> MongoDBObject("$exists" -> false))
      case otherwise            => throw new NotImplementedError
    }

    MongoDBObject("$match" -> query)
  }

  /** Sort operation for sample documents */
  private[processors] val opSortSample = MongoDBObject("$sort" -> MongoDBObject("creationTimeUtc" -> -1))

  /** Projection operation for selecting only libraries */
  private[processors] val opProjectLibs = MongoDBObject("$project" ->
    MongoDBObject("_id" -> 0, "runId" -> 1, "uploaderId" -> 1, "libs" -> 1))

  /** Unwind operation to break open libs array */
  private[processors] val opUnwindLibs = MongoDBObject("$unwind" -> "$libs")

  /** Map function for mapReduce on alnStats */
  private[processors] def mapFuncAlnStats(attr: String, accLevel: AccLevel.Value,
                                          libType: Option[LibType.Value]): JSFunction = {

    val jsArrayStr = accLevel match {
      case AccLevel.Sample => "[this]"
      case AccLevel.Lib    => "this.libs"
      case otherwise       => throw new NotImplementedError
    }

    val jsSingleStr = libType match {
      case None                 => "undefined"
      case Some(LibType.Paired) => "false"
      case Some(LibType.Single) => "true"
      case otherwise            => throw new NotImplementedError
    }

    s"""function map() {
    |  $jsArrayStr.forEach(function(item) {
    |
    |    var passFilter = item.alnStats.$attr !== null && item.alnStats.$attr !== undefined;
    |
    |    if ($jsSingleStr === undefined) {
    |      passFilter = passFilter && true
    |    } else if ($jsSingleStr === true) {
    |      passFilter = passFilter && (item.seqStatsRaw.read2 === undefined)
    |    } else {
    |      passFilter = passFilter && (item.seqStatsRaw.read2 !== undefined)
    |    }
    |
    |    if (passFilter) {
    |      emit("$attr",
    |        {
    |          sum: item.alnStats.$attr,
    |          min: item.alnStats.$attr,
    |          max: item.alnStats.$attr,
    |          arr: [item.alnStats.$attr],
    |          nDataPoints: 1,
    |          diff: 0
    |        });
    |    }
    |  });
    |}
    """.stripMargin
  }

  /** Map function for mapReduce on seqStats */
  private[processors] def mapFuncSeqStats(attr: String, readName: String, qcPhase: SeqQcPhase.Value,
                                          libType: Option[LibType.Value]): JSFunction = {

    val jsSingleStr = libType match {
      case None                 => "undefined"
      case Some(LibType.Paired) => "false"
      case Some(LibType.Single) => "true"
      case otherwise            => throw new NotImplementedError
    }

    val jsStatsName = qcPhase match {
      case SeqQcPhase.Raw       => "seqStatsRaw"
      case SeqQcPhase.Processed => "seqStatsProcessed"
      case otherwise            => throw new NotImplementedError
    }

    s"""function map() {
    |  this.libs.forEach(function(item) {
    |
    |    var passFilter = item.$jsStatsName !== null && item.$jsStatsName !== undefined;
    |
    |    if (passFilter) {
    |      passFilter = passFilter && (item.$jsStatsName.$readName !== null && item.$jsStatsName.$readName !== undefined);
    |    }
    |
    |    if (passFilter) {
    |      passFilter = passFilter && (item.$jsStatsName.$readName.$attr !== null && item.$jsStatsName.$readName.$attr !== undefined);
    |    }
    |
    |    if (passFilter) {
    |     if ($jsSingleStr === undefined) {
    |       passFilter = passFilter && true;
    |     } else if ($jsSingleStr === true) {
    |       passFilter = passFilter && (item.$jsStatsName.read2 === undefined);
    |     } else {
    |       passFilter = passFilter && (item.$jsStatsName.read2 !== undefined);
    |     }
    |   }
    |
    |    if (passFilter) {
    |      emit("$attr",
    |        {
    |          sum: item.$jsStatsName.$readName.$attr,
    |          min: item.$jsStatsName.$readName.$attr,
    |          max: item.$jsStatsName.$readName.$attr,
    |          arr: [item.$jsStatsName.$readName.$attr],
    |          nDataPoints: 1,
    |          diff: 0
    |        });
    |      }
    |  });
    |}
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
   * Retrieves Gentrap alignment statistics.
   *
   * @param accLevel Accumulation level of the retrieved statistics.
   * @param libType Library type of the retrieved statistics. If not specified, all library types are used.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param runs Run IDs of the returned statistics. If not specified, alignment statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, alignment statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, alignment statistics are not
   *                    filtered by annotation IDs.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @return Sequence of alignment statistics objects.
   */
  def getAlignmentStats(accLevel: AccLevel.Value,
                        libType: Option[LibType.Value] = None,
                        user: Option[User] = None,
                        runs: Seq[ObjectId] = Seq(),
                        references: Seq[ObjectId] = Seq(),
                        annotations: Seq[ObjectId] = Seq(),
                        timeSorted: Boolean = false): Seq[GentrapAlignmentStats] = {

    // Match operation to filter for run, reference, and/or annotation IDs
    val opMatchFilters = buildMatchOp(runs, references, annotations)

    val operations = accLevel match {

      case AccLevel.Sample =>
        val opProjectAlnStats = MongoDBObject("$project" ->
          MongoDBObject("_id" -> 0, "alnStats" -> 1, "uploaderId" -> 1,
            "labels" -> MongoDBObject("runId" -> "$runId", "runName" -> "$runName", "sampleName" -> "$sampleName")))
        if (timeSorted)
          Seq(opMatchFilters, opSortSample, opProjectAlnStats)
        else
          Seq(opMatchFilters, opProjectAlnStats)

      case AccLevel.Lib =>
        val opProjectStats = MongoDBObject("$project" ->
          MongoDBObject("alnStats" -> "$libs.alnStats", "uploaderId" -> "$uploaderId", "labels" ->
            MongoDBObject("runId" -> "$runId", "runName" ->
              "$libs.runName", "sampleName" -> "$libs.sampleName", "libName" -> "$libs.libName")))
        if (timeSorted)
          Seq(opMatchFilters, opProjectLibs, opUnwindLibs, buildMatchPostUnwindOp(libType), opProjectStats)
        else
          Seq(opMatchFilters, opSortSample, opProjectLibs, opUnwindLibs, buildMatchPostUnwindOp(libType), opProjectStats)

      case otherwise => throw new NotImplementedError
    }

    lazy val results = coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map {
        case aggres =>
          val uploaderId = aggres.getAs[String]("uploaderId")
          val labels = aggres.getAs[DBObject]("labels")
          val astat = aggres.getAs[DBObject]("alnStats")
          val dbo = (user, uploaderId, astat, labels) match {
            case (Some(u), Some(uid), Some(s), Some(n)) =>
              if (u.id == uid) Option(s ++ MongoDBObject("labels" -> n))
              else Option(s)
            case (None, _, Some(s), _) => Option(s)
            case otherwise             => None
          }
          dbo.collect {
            case obj => grater[GentrapAlignmentStats].asObject(obj)
          }
      }.toSeq.flatten

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves aggregated Gentrap alignment statistics.
   *
   * @param accLevel Accumulation level of the retrieved statistics.
   * @param libType Library type of the retrieved statistics. If not specified, all library types are used.
   * @param runs Run IDs of the returned statistics. If not specified, alignment statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, alignment statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, alignment statistics are not
   *                    filtered by annotation IDs.
   * @return Alignment statistics aggregates.
   */
  def getAlignmentAggregateStats(accLevel: AccLevel.Value,
                                 libType: Option[LibType.Value],
                                 runs: Seq[ObjectId] = Seq(),
                                 references: Seq[ObjectId] = Seq(),
                                 annotations: Seq[ObjectId] = Seq()): Option[GentrapAlignmentStatsAggr] = {

    val query = buildMatchOp(runs, references, annotations, withKey = false)

    def mapRecResults(attr: String) = coll
      .mapReduce(
        mapFunction = mapFuncAlnStats(attr, accLevel, libType),
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

    // TODO: generate the attribute names programmatically (using macros?)
    val attrs = Seq("nReads",
      "nReadsAligned",
      "rateReadsMismatch",
      "rateIndel",
      "nBasesAligned",
      "nBasesUtr",
      "nBasesCoding",
      "nBasesIntron",
      "nBasesIntergenic",
      "nBasesRibosomal",
      "median5PrimeBias",
      "median3PrimeBias",
      "pctChimeras",
      "nReadsSingleton",
      "nReadsProperPair",
      "maxInsertSize",
      "medianInsertSize",
      "stdevInsertSize")

    val aggrStats = attrs.par
      .flatMap { mapRecResults }
      .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }

    if (aggrStats.isEmpty) None
    else Some(grater[GentrapAlignmentStatsAggr].asObject(aggrStats))
  }

  /**
   * Retrieves Gentrap sequence statistics.
   *
   * Each sample entry in the database contains an array of library entries, which in turn contain the sequence
   * statistics. So to retrieve the statistics only, this method does some aggregation operations.
   *
   * Note that constructing database operations is very prone to runtime errors since we are mostly only stringing
   * together MongoDBObjects and strings. As such, this method must be tested thoroughly.
   *
   * @param libType Library type of the returned sequence statistics.
   * @param qcPhase Sequencing QC phase of the returned statistics.
   * @param user If defined, returned data points belonging to the user will show its labels.
   * @param runs Run IDs of the returned statistics. If not specified, sequence statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, sequence statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, sequence statistics are not
   *                    filtered by annotation IDs.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @return Sequence of sequence statistics objects.
   */
  def getSeqStats(libType: Option[LibType.Value],
                  qcPhase: SeqQcPhase.Value,
                  user: Option[User],
                  runs: Seq[ObjectId] = Seq(),
                  references: Seq[ObjectId] = Seq(),
                  annotations: Seq[ObjectId] = Seq(),
                  timeSorted: Boolean = false): Seq[SeqStats] = {

    // Match operation to filter for run, reference, and/or annotation IDs
    val opMatchFilters = buildMatchOp(runs, references, annotations)

    // Match operation for selecting library type
    val opMatchLibType = buildMatchPostUnwindOp(libType)

    // Projection operation for retrieving innermost stats object
    val opProjectStats = {

      // attrName is the name of the processed/raw sequence objects in the library document
      val attrName =
        if (qcPhase == SeqQcPhase.Raw) "seqStatsRaw"
        else if (qcPhase == SeqQcPhase.Processed) "seqStatsProcessed"
        else throw new RuntimeException("Unexpected sequencing QC phase value: " + qcPhase.toString)

      MongoDBObject("$project" ->
        MongoDBObject(
          "labels" -> MongoDBObject(
            "runId" -> "$runId",
            "runName" -> "$libs.runName",
            "sampleName" -> "$libs.sampleName",
            "libName" -> "$libs.libName"),
          "uploaderId" -> "$uploaderId",
          "read1" -> ("$libs." + attrName + ".read1"),
          "read2" -> ("$libs." + attrName + ".read2")))
    }

    val operations = timeSorted match {
      case true  => Seq(opMatchFilters, opSortSample, opProjectLibs, opUnwindLibs, opMatchLibType, opProjectStats)
      case false => Seq(opMatchFilters, opProjectLibs, opUnwindLibs, opMatchLibType, opProjectStats)
    }

    lazy val results = coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map {
        case pstat =>
          val dbo = (user, pstat.getAs[String]("uploaderId")) match {
            case (Some(usr), Some(uid)) if usr.id == uid => pstat
            case otherwise                               => (pstat - "labels").asDBObject
          }
          grater[SeqStats].asObject(dbo)
      }.toSeq

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

  /**
   * Retrieves aggregated Gentrap sequence statistics.
   *
   * @param libType Library type of the returned sequence statistics.
   * @param qcPhase Sequencing QC phase of the returned statistics.
   * @param runs Run IDs of the returned statistics. If not specified, sequence statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, sequence statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, sequence statistics are not
   *                    filtered by annotation IDs.
   * @return Sequence statistics aggregates.
   */
  def getSeqAggregateStats(libType: Option[LibType.Value],
                           qcPhase: SeqQcPhase.Value,
                           runs: Seq[ObjectId] = Seq(),
                           references: Seq[ObjectId] = Seq(),
                           annotations: Seq[ObjectId] = Seq()): Option[SeqStatsAggr] = {

    val query = buildMatchOp(runs, references, annotations, withKey = false)

    def mapRecResults(attr: String, readName: String) = coll
      .mapReduce(
        mapFunction = mapFuncSeqStats(attr, readName, qcPhase, libType),
        reduceFunction = reduceFunc,
        output = MapReduceInlineOutput,
        finalizeFunction = Option(finalizeFunc),
        query = Option(query))
      .toSeq
      .headOption
      .collect { case res => MongoDBObject(attr -> res.getAsOrElse[MongoDBObject]("value", MongoDBObject.empty)) }

    // TODO: generate the attribute names programmatically (using macros?)
    val attrs = Seq("nReads",
      "nBases",
      "nBasesA",
      "nBasesT",
      "nBasesG",
      "nBasesC",
      "nBasesN")

    val readNames = Seq("read1", "read2")

    val aggrStats = readNames.par
      .map {
        case rn =>
          val res = attrs.par
            .flatMap { case an => mapRecResults(an, rn) }
            .foldLeft(MongoDBObject.empty) { case (acc, x) => acc ++ x }
          if (res.nonEmpty) (rn, Option(grater[ReadStatsAggr].asObject(res)))
          else (rn, None)
      }.seq.toMap

    aggrStats("read1").collect { case r1 => SeqStatsAggr(read1 = r1, read2 = aggrStats("read2")) }
  }
}
