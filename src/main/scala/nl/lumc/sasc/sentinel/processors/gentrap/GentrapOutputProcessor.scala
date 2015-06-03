package nl.lumc.sasc.sentinel.processors.gentrap

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import nl.lumc.sasc.sentinel.models.SeqStats

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.db.{ MongodbAccessObject, MongodbConnector }

import scala.collection.mutable.ListBuffer

class GentrapOutputProcessor(protected val mongo: MongodbAccessObject) extends MongodbConnector {

  private lazy val coll = mongo.db(collectionNames.pipelineSamples("gentrap"))

  // TODO: Implement random ordering of returned values
  def getAlignmentStats(accLevel: AccLevel.Value,
                        runs: Seq[String] = Seq(),
                        references: Seq[String] = Seq(),
                        annotations: Seq[String] = Seq()): Seq[GentrapAlignmentStats] = {

    val queryBuilder = MongoDBObject.newBuilder
    if (runs.size > 0) queryBuilder += "runId" -> runs

    coll
      .find(queryBuilder.result(), MongoDBObject("alnStats" -> 1))
      .map {
        case dbo =>
          dbo.get("alnStats") match {
            case alnStats: BasicDBObject => grater[GentrapAlignmentStats].asObject(alnStats)
          }
      }
      .toSeq
  }

  /**
   * Match operation builder for collection aggregations.
   *
   * @param runs Run IDs to filter in. If empty, no run ID filtering is done.
   * @param references Reference IDs to filter in. If empty, no reference ID filtering is done.
   * @param annotations Annotation IDs to filter in. If empty, no annotation ID filtering is done.
   * @return a [[DBObject]] representing the `$match` aggregation operation.
   */
  private[processors] def buildMatchOp(runs: Seq[ObjectId], references: Seq[ObjectId],
                                       annotations: Seq[ObjectId]): DBObject = {
    val matchBuffer = new ListBuffer[MongoDBObject]()

    if (runs.nonEmpty)
      matchBuffer += MongoDBObject("runId" -> MongoDBObject("$in" -> runs))

    if (references.nonEmpty)
      matchBuffer += MongoDBObject("referenceId" -> MongoDBObject("$in" -> references))

    if (annotations.nonEmpty)
      matchBuffer += MongoDBObject("annotationIds" ->
        MongoDBObject("$elemMatch" -> MongoDBObject("$in" -> annotations)))

    MongoDBObject("$match" -> {
      if (matchBuffer.nonEmpty) MongoDBObject("$and" -> matchBuffer.toSeq)
      else MongoDBObject.empty
    })
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
   * @return a sequence of [[SeqStats]] objects.
   */
  def getSeqStats(libType: LibType.Value,
                  qcPhase: SeqQcPhase.Value,
                  runs: Seq[ObjectId] = Seq(),
                  references: Seq[ObjectId] = Seq(),
                  annotations: Seq[ObjectId] = Seq()): Seq[SeqStats] = {

    // Match operation to filter for run, reference, and/or annotation IDs
    val opMatchFilters = buildMatchOp(runs, references, annotations)

    // Projection operation for selecting only libraries
    val opProjectLibs = MongoDBObject("$project" -> MongoDBObject("_id" -> 0, "libs" -> 1))

    // Unwind operation to break open libs array
    val opUnwindLibs = MongoDBObject("$unwind" -> "$libs")

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

    // Match operation for selecting library type
    val opMatchLibType =
      if (libType == LibType.Paired)
        MongoDBObject("$match" -> MongoDBObject("read2" -> MongoDBObject("$exists" -> true)))
      else if (libType == LibType.Single)
        MongoDBObject("$match" -> MongoDBObject("read2" -> MongoDBObject("$exists" -> false)))
      else
        throw new RuntimeException("Unexpected library type value: " + libType.toString)

    val operations = Seq(opMatchFilters, opProjectLibs, opUnwindLibs, opProjectStats, opMatchLibType)

    coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map { case pstat => grater[SeqStats].asObject(pstat) }
      .toSeq
  }
}
