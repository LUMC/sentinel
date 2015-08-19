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
import com.typesafe.config.ConfigFactory
import javax.servlet.ServletContext
import org.scalatra.LifeCycle
import org.scalatra.swagger.ApiKey
import scala.util.Try

import nl.lumc.sasc.sentinel.{ HeaderApiKey, settings }, settings._
import nl.lumc.sasc.sentinel.api._
import nl.lumc.sasc.sentinel.biopet.gentrap.GentrapRunsProcessor
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject
import nl.lumc.sasc.sentinel.utils.reflect.makeDelayedProcessor

/** Main entry point for mounted servlets. */
class ScalatraBootstrap extends LifeCycle {

  /** Container for main Swagger specifications. */
  implicit val swagger = new SentinelSwagger
  // TODO: how to add this in the object definitions itself?
  swagger.addAuthorization(ApiKey(HeaderApiKey, "header"))

  override def init(context: ServletContext) {

    val conf = ConfigFactory.load()

    /** Deployment environment, 'production' or 'development'. */
    val env = Try(conf.getString(s"$SentinelConfKey.env")).getOrElse("development")

    implicit val mongo = MongodbAccessObject.withDefaultSettings
    implicit val system = ActorSystem("appActorSystem")
    implicit val runsProcessors = Set(makeDelayedProcessor[GentrapRunsProcessor])

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
      context mount (new ApiSpecsController, "/api-specs/*")
      context mount (new ApiDocsController, "/api-docs/*")
      context setInitParameter (org.scalatra.EnvironmentKey, env)
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
