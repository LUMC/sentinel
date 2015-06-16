import akka.actor.ActorSystem
import com.mongodb.casbah.MongoClient
import javax.servlet.ServletContext
import org.scalatra.LifeCycle
import org.scalatra.swagger.ApiKey

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api._
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

/** Main entry point for mounted servlets. */
class ScalatraBootstrap extends LifeCycle {

  /** Container for main Swagger specifications. */
  implicit val swagger = new SentinelSwagger
  // TODO: how to add this in the object definitions itself?
  swagger.addAuthorization(ApiKey(HeaderApiKey, "header"))

  override def init(context: ServletContext) {

    implicit val system = ActorSystem("appActorSystem")
    // TODO: refactor this out into a config file
    implicit val mongo = MongodbAccessObject(MongoClient("localhost", 27017), "sentinel")

    // TODO: separate production and development behavior more cleanly
    try {
      context mount (new RootController, "/*")
      context mount (new StatsController, "/stats/*")
      context mount (new ReferencesController, "/references/*")
      context mount (new AnnotationsController, "/annotations/*")
      context mount (new RunsController, "/runs/*")
      context mount (new UsersController, "/users/*")
      context mount (new ResourcesApp, "/api-docs/*")
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
