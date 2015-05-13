package nl.lumc.sasc.sentinel.processors.gentrap

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import nl.lumc.sasc.sentinel.models.SeqStats

import nl.lumc.sasc.sentinel.{ AccLevel, LibType, SeqQcPhase }
import nl.lumc.sasc.sentinel.db.{ MongodbAccessObject, MongodbConnector }


class GentrapOutputProcessor(protected val mongo: MongodbAccessObject) extends MongodbConnector {

  def samplesCollectionName = GentrapSamplesCollectionName

  private lazy val coll = mongo.db(samplesCollectionName)

  // TODO: Implement random ordering of returned values
  def getAlignmentStats(accLevel: AccLevel.Value,
                        runs: Seq[String] = Seq(),
                        references: Seq[String] = Seq(),
                        annotations: Seq[String] = Seq()): Seq[GentrapAlignmentStats] = {

    val queryBuilder = MongoDBObject.newBuilder
    if (runs.size > 0) queryBuilder += "runId" -> runs

    coll
      .find(queryBuilder.result(), MongoDBObject("alnStats" -> 1))
      .map { case dbo =>
        dbo.get("alnStats") match {
          case alnStats: BasicDBObject => grater[GentrapAlignmentStats].asObject(alnStats)
        }
      }
      .toSeq
  }

  def getSeqStats(libType: LibType.Value,
                  qcPhase: SeqQcPhase.Value,
                  runs: Seq[String] = Seq(),
                  references: Seq[String] = Seq(),
                  annotations: Seq[String] = Seq()): Seq[SeqStats] = {

    // TODO: Optimize query to do the filtering and selection on the db-side
    def libToSeqStats(lib: GentrapLibDocument): Option[SeqStats] =
        if (qcPhase == SeqQcPhase.Raw) Option(lib.rawStats)
        else lib.processedStats

    def libTypeOk(lib: GentrapLibDocument): Boolean =
      if (libType == LibType.Paired) lib.rawSeq.read2.isDefined
      else lib.rawSeq.read2.isEmpty

    // TODO: Reduce type case matches ~ prone to runtime errors!
    def libsFromDbo(dbo: DBObject): Seq[GentrapLibDocument] =
      dbo.get("libs") match {
        case libs: BasicDBList =>  libs
          .map { case lib: BasicDBObject => grater[GentrapLibDocument].asObject(lib) }
      }

    coll
      .find(MongoDBObject.empty, MongoDBObject("libs" -> 1))
      .map { libsFromDbo }.flatten
      .filter { libTypeOk }
      .map { libToSeqStats }.flatten
      .toSeq
  }
}
