package nl.lumc.sasc.sentinel.processors.gentrap

import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._

import nl.lumc.sasc.sentinel.AccLevel
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

}
