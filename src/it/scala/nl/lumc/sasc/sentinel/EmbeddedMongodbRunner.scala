package nl.lumc.sasc.sentinel

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.Imports._
import de.flapdoodle.embed.mongo.{ Command, MongodStarter }
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network
import org.slf4j.helpers.NOPLoggerFactory

import nl.lumc.sasc.sentinel.db.MongodbAccessObject

trait EmbeddedMongodbRunner {

  protected val mongodVersion = Version.Main.V3_0

  protected val dbName = "sentinel_testing"

  private lazy val mongodPort: Int = {

    @tailrec def getFreePort: Int = Try(Network.getFreeServerPort) match {
      case Success(port) => port
      case Failure(_)    => getFreePort
    }

    getFreePort
  }

  private val runtimeConfig = new RuntimeConfigBuilder()
    .defaultsWithLogger(Command.MongoD, (new NOPLoggerFactory).getLogger("nop"))
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build()

  private val mongodConfig = new MongodConfigBuilder()
    .version(mongodVersion)
    .net(new Net(mongodPort, Network.localhostIsIPv6))
    .build()

  private lazy val starter = MongodStarter.getInstance(runtimeConfig)

  private def createIndices(mongo: MongodbAccessObject): Unit = {
    mongo.db("fs.files").createIndex(MongoDBObject("md5" -> 1, "metadata.userId" -> 1), MongoDBObject("unique" -> true))
    mongo.db("annotations").createIndex(MongoDBObject("annotMd5" -> 1), MongoDBObject("unique" -> true))
    mongo.db("references").createIndex(MongoDBObject("combinedMd5" -> 1), MongoDBObject("unique" -> true))
  }

  private lazy val mongodExecutable = starter.prepare(mongodConfig)

  protected lazy val mongoClient = MongoClient("localhost", mongodPort)

  protected lazy val dbAccess = MongodbAccessObject(mongoClient, dbName)

  protected def resetDb(): Unit = {
    dbAccess.db.getCollectionNames()
      .filterNot(_.startsWith("system"))
      .foreach { case collName => dbAccess.db(collName).dropCollection() }
    createIndices(dbAccess)
  }

  def start(): Unit = {
    mongodExecutable.start()
    createIndices(dbAccess)
  }

  def stop(): Unit = mongodExecutable.stop()
}
