package nl.lumc.sasc.sentinel.api.auth

import scala.language.reflectiveCalls

import org.scalatra.auth.{ ScentryConfig, ScentrySupport }

import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.getTimeNow

trait AuthenticationSupport extends ScentrySupport[User]
    with BasicAuthSupport[User]
    with SimpleKeyAuthSupport[User] { this: SentinelServlet { def users: UsersAdapter } =>

  protected val scentryConfig = new ScentryConfig {}.asInstanceOf[ScentryConfiguration]

  override protected def registerAuthStrategies() = {
    scentry.register(new SimpleKeyAuthStrategy(this))
    scentry.register(new BasicAuthStrategy(this, "Sentinel users"))
  }

  override protected def configureScentry() = {
    scentry.store = new BlankAuthStore
  }

  // NOTE: Scentry caches authentication and we've turned it off using our BlankAuthStore. But these two methods
  //       still needs to be implemented, so we are implementing something minimum
  protected def fromSession = {
    case id: String =>
      User(id, "", "", "", verified = false, isAdmin = false, getTimeNow)
  }
  protected def toSession = { case user: User => user.id }

}
