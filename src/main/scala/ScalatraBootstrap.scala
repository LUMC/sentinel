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
import akka.actor.ActorSystem
import com.mongodb.{ MongoCredential, ServerAddress }
import com.mongodb.casbah.MongoClient
import com.typesafe.config.ConfigFactory
import javax.servlet.ServletContext
import org.scalatra.LifeCycle
import org.scalatra.swagger.ApiKey
import scala.util.Try

import nl.lumc.sasc.sentinel.{ HeaderApiKey, settings }, settings._
import nl.lumc.sasc.sentinel.api._
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

/** Main entry point for mounted servlets. */
class ScalatraBootstrap extends LifeCycle {

  /** Container for main Swagger specifications. */
  implicit val swagger = new SentinelSwagger
  // TODO: how to add this in the object definitions itself?
  swagger.addAuthorization(ApiKey(HeaderApiKey, "header"))

  override def init(context: ServletContext) {

    val conf = ConfigFactory.load()

    /** Database server hostname. */
    val host = Try(conf.getString(s"$DbConfKey.host")).getOrElse("localhost")

    /** Database server port. */
    val port = Try(conf.getInt(s"$DbConfKey.port")).getOrElse(27017)

    /** Database name. */
    val dbName = Try(conf.getString(s"$DbConfKey.dbName")).getOrElse("sentinel")

    /** Username for database server. */
    val userName = Try(conf.getString(s"$DbConfKey.userName")).toOption

    /** Password for database authentication. */
    val password = Try(conf.getString(s"$DbConfKey.password")).toOption

    /** Deployment environment, 'production' or 'development'. */
    val env = Try(conf.getString(s"$SentinelConfKey.env")).getOrElse("development")

    // Create authenticated connection only when both userName and password are supplied
    val addr = new ServerAddress(host, port)
    val client = (userName, password) match {
      case (Some(usr), Some(pwd)) =>
        val cred = MongoCredential.createScramSha1Credential(usr, dbName, pwd.toCharArray)
        MongoClient(addr, List(cred))
      case otherwise => MongoClient(addr)
    }

    implicit val mongo = MongodbAccessObject(client, dbName)
    implicit val system = ActorSystem("appActorSystem")

    // Check that we have a live connection to the DB
    mongo.db.getStats()

    // TODO: separate production and development behavior more cleanly
    try {
      context mount (new RootController, "/*")
      context mount (new StatsController, "/stats/*")
      context mount (new ReferencesController, "/references/*")
      context mount (new AnnotationsController, "/annotations/*")
      context mount (new RunsController, "/runs/*")
      context mount (new UsersController, "/users/*")
      context mount (new ResourcesApp, "/api-spec/*")
      context setInitParameter (org.scalatra.EnvironmentKey, env)
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
