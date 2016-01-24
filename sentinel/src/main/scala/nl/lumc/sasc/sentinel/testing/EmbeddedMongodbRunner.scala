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
package nl.lumc.sasc.sentinel.testing

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

import com.mongodb.casbah.Imports._
import de.flapdoodle.embed.mongo.{ Command, MongodStarter }
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network
import org.slf4j.helpers.NOPLoggerFactory

import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

trait EmbeddedMongodbRunner {

  protected val mongodVersion = Version.Main.V3_2

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
    mongo.db("fs.files").createIndex(MongoDBObject("md5" -> 1, "metadata.uploaderId" -> 1), MongoDBObject("unique" -> true))
    mongo.db("annotations").createIndex(MongoDBObject("annotMd5" -> 1), MongoDBObject("unique" -> true))
    mongo.db("references").createIndex(MongoDBObject("combinedMd5" -> 1), MongoDBObject("unique" -> true))
  }

  private lazy val mongodExecutable = starter.prepare(mongodConfig)

  protected lazy val mongoClient = MongoClient("localhost", mongodPort)

  protected lazy val dao = MongodbAccessObject(mongoClient, dbName)

  protected def resetDatabase(): Unit = {
    dao.db.getCollectionNames()
      .filterNot(_.startsWith("system"))
      .foreach { case collName => dao.db(collName).dropCollection() }
    createIndices(dao)
  }

  def start(): Unit = {
    mongodExecutable.start()
    createIndices(dao)
  }

  def stop(): Unit = mongodExecutable.stop()
}
