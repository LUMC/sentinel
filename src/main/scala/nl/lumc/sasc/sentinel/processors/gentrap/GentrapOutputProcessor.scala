package nl.lumc.sasc.sentinel.processors.gentrap

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import nl.lumc.sasc.sentinel.models.SeqStats

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.db.{ MongodbAccessObject, MongodbConnector }

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
                  runs: Seq[String] = Seq(),
                  references: Seq[String] = Seq(),
                  annotations: Seq[String] = Seq()): Seq[SeqStats] = {

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

    val operations = Seq(
      MongoDBObject("$project" -> MongoDBObject("_id" -> 0, "libs" -> 1)),
      MongoDBObject("$unwind" -> "$libs"),
      opProjectStats, opMatchLibType)

    coll
      .aggregate(operations, AggregationOptions(AggregationOptions.CURSOR))
      .map { case pstat => grater[SeqStats].asObject(pstat) }
      .toSeq
  }
}
