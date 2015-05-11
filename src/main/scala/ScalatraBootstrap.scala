import akka.actor.ActorSystem
import com.mongodb.casbah.MongoClient
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import nl.lumc.sasc.sentinel.api._
import nl.lumc.sasc.sentinel.db.MongodbAccessObject

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new SentinelSwagger

  val conn = MongodbAccessObject(MongoClient("localhost", 27017), "sentinel")

  override def init(context: ServletContext) {

    implicit val system = ActorSystem("appActorSystem")

    try {
      context mount (new RootController, "/*")
      context mount (new StatsController, "/stats/*")
      context mount (new ReferencesController, "/references/*")
      context mount (new AnnotationsController(conn), "/annotations/*")
      context mount (new RunsController(conn), "/runs/*")
      context mount (new UsersController, "/users/*")
      context mount (new ResourcesApp, "/api-docs/*")
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
