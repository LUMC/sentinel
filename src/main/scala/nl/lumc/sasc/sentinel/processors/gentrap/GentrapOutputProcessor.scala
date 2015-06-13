package nl.lumc.sasc.sentinel.processors.gentrap

import scala.util.Random.shuffle

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.db.{ MongodbAccessObject, MongodbConnector }
import nl.lumc.sasc.sentinel.models._

import scala.collection.mutable.ListBuffer

class GentrapOutputProcessor(protected val mongo: MongodbAccessObject) extends MongodbConnector {

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
      case Some(LibType.Paired) => MongoDBObject("libs.rawSeq.read2" -> MongoDBObject("$exists" -> true))
      case Some(LibType.Single) => MongoDBObject("libs.rawSeq.read2" -> MongoDBObject("$exists" -> false))
      case otherwise            => throw new NotImplementedError
    }

    MongoDBObject("$match" -> query)
  }

  /** Sort operation for sample documents */
  private[processors] val opSortSample = MongoDBObject("$sort" -> MongoDBObject("creationTimeUtc" -> -1))

  /** Projection operation for selecting only libraries */
  private[processors] val opProjectLibs = MongoDBObject("$project" -> MongoDBObject("_id" -> 0, "libs" -> 1))

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
    |      passFilter = passFilter && (item.rawSeq.read2 === undefined)
    |    } else {
    |      passFilter = passFilter && (item.rawSeq.read2 !== undefined)
    |    }
    |
    |    if (passFilter) {
    |      emit("$attr",
    |        {
    |          sum: item.alnStats.$attr,
    |          min: item.alnStats.$attr,
    |          max: item.alnStats.$attr,
    |          arr: [item.alnStats.$attr],
    |          count: 1,
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

    val jsSeqName = qcPhase match {
      case SeqQcPhase.Raw       => "rawSeq"
      case SeqQcPhase.Processed => "processedSeq"
      case otherwise            => throw new NotImplementedError
    }

    s"""function map() {
    |  this.libs.forEach(function(item) {
    |
    |    var passFilter = item.$jsSeqName !== null && item.$jsSeqName !== undefined;
    |
    |    if (passFilter) {
    |      passFilter = passFilter && (item.$jsSeqName.$readName !== null && item.$jsSeqName.$readName !== undefined);
    |    }
    |
    |    if (passFilter) {
    |      passFilter = passFilter && (item.$jsSeqName.$readName.stats.$attr !== null && item.$jsSeqName.$readName.stats.$attr !== undefined);
    |    }
    |
    |    if (passFilter) {
    |     if ($jsSingleStr === undefined) {
    |       passFilter = passFilter && true;
    |     } else if ($jsSingleStr === true) {
    |       passFilter = passFilter && (item.$jsSeqName.read2 === undefined);
    |     } else {
    |       passFilter = passFilter && (item.$jsSeqName.read2 !== undefined);
    |     }
    |   }
    |
    |    if (passFilter) {
    |      emit("$attr",
    |        {
    |          sum: item.$jsSeqName.$readName.stats.$attr,
    |          min: item.$jsSeqName.$readName.stats.$attr,
    |          max: item.$jsSeqName.$readName.stats.$attr,
    |          arr: [item.$jsSeqName.$readName.stats.$attr],
    |          count: 1,
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
      |    var delta = a.sum / a.count - b.sum / b.count;
      |    var weight = (a.count * b.count) / (a.count + b.count);
      |
      |    // do the reducing
      |    a.diff += b.diff + delta * delta * weight;
      |    a.sum += b.sum;
      |    a.count += b.count;
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
      |  value.mean = value.sum / value.count;
      |  value.variance = value.diff / value.count;
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

  def getAlignmentStats(accLevel: AccLevel.Value,
                        libType: Option[LibType.Value],
                        user: Option[User],
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
            "names" -> MongoDBObject("runName" -> "$runName", "sampleName" -> "$sampleName")))
        if (timeSorted)
          Seq(opMatchFilters, opSortSample, opProjectAlnStats)
        else
          Seq(opMatchFilters, opProjectAlnStats)

      case AccLevel.Lib =>
        val opProjectStats = MongoDBObject("$project" ->
          MongoDBObject("alnStats" -> "$libs.alnStats", "uploaderId" -> "$libs.uploaderId", "names" ->
            MongoDBObject("runName" ->
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
          val names = aggres.getAs[DBObject]("names")
          val astat = aggres.getAs[DBObject]("alnStats")
          val dbo = (user, uploaderId, astat, names) match {
            case (Some(u), Some(uid), Some(s), Some(n)) =>
              if (u.id == uid) Option(s ++ MongoDBObject("names" -> n))
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
      "nSingletons",
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
   * Retrieves sequence statistics from database sample entries.
   *
   * Each sample entry in the database contains an array of library entries, which in turn contain the sequence
   * statistics. So to retrieve the statistics only, this method does some aggregation operations.
   *
   * Note that constructing database operations is very prone to runtime errors since we are mostly only stringing
   * together MongoDBObjects and strings. As such, this method must be tested thoroughly.
   *
   * @param libType Library type of the returned sequence statistics.
   * @param qcPhase Sequencing QC phase of the returned statistics.
   * @param runs Run IDs of the returned statistics. If not specified, sequence statistics are not filtered by run ID.
   * @param references Reference IDs of the returned statistics. If not specified, sequence statistics are not filtered
   *                   by reference IDs.
   * @param annotations Annotations IDs of the returned statistics. If not specified, sequence statistics are not
   *                    filtered by annotation IDs.
   * @param timeSorted Whether to time-sort the returned items or not.
   * @return a sequence of [[SeqStats]] objects.
   */
  def getSeqStats(libType: Option[LibType.Value],
                  qcPhase: SeqQcPhase.Value,
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
        if (qcPhase == SeqQcPhase.Raw) "rawSeq"
        else if (qcPhase == SeqQcPhase.Processed) "processedSeq"
        else throw new RuntimeException("Unexpected sequencing QC phase value: " + qcPhase.toString)

      MongoDBObject("$project" ->
        MongoDBObject(
          "read1" -> ("$libs." + attrName + ".read1.stats"),
          "read2" -> ("$libs." + attrName + ".read2.stats")))
    }

    val operations = timeSorted match {
      case true  => Seq(opMatchFilters, opSortSample, opProjectLibs, opUnwindLibs, opMatchLibType, opProjectStats)
      case false => Seq(opMatchFilters, opProjectLibs, opUnwindLibs, opMatchLibType, opProjectStats)
    }

    lazy val results = coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map { case pstat => grater[SeqStats].asObject(pstat) }
      .toSeq

    // TODO: switch to database-level randomization when SERVER-533 is resolved
    if (timeSorted) results
    else shuffle(results)
  }

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
