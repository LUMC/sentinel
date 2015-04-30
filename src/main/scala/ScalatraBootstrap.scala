import akka.actor.ActorSystem
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import nl.lumc.sasc.sentinel.api._

class ScalatraBootstrap extends LifeCycle {
  implicit val swagger = new SentinelSwagger

  override def init(context: ServletContext) {
    implicit val system = ActorSystem("appActorSystem")
    try {
      context mount (new RootController, "/*")
      context mount (new StatsController, "/stats/*")
      context mount (new ReferencesController, "/references/*")
      context mount (new RunsController, "/runs/*")
      context mount (new ResourcesApp, "/api-docs/*")
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
