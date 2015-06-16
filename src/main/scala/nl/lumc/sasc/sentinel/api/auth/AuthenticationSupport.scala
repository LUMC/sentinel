package nl.lumc.sasc.sentinel.api.auth

import scala.language.reflectiveCalls

import org.scalatra.auth.{ ScentryConfig, ScentrySupport }

import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

/**
 * Authentication trait for Sentinel controllers.
 *
 * Supported authentication methods are Basic HTTP and API key authentication (via parameters in the
 * HTTP header and request URL). Controllers mixing in this trait must define a `users` field which is an instance
 * of the [[nl.lumc.sasc.sentinel.db.UsersAdapter]] object.
 *
 * This trait is meant to be mixed with any Sentinel controller that needs to secure one or more of its endpoints with
 * authentication.
 */
trait AuthenticationSupport extends ScentrySupport[User]
    with BasicAuthSupport[User]
    with SimpleKeyAuthSupport[User] { this: SentinelServlet { def users: UsersAdapter } =>

  /** Scentry configuration instance used by the trait. */
  protected val scentryConfig = new ScentryConfig {}.asInstanceOf[ScentryConfiguration]

  /** Registers our custom authentication strategies. */
  override protected def registerAuthStrategies() = {
    scentry.register(new SimpleKeyAuthStrategy(this))
    scentry.register(new BasicAuthStrategy(this, "Sentinel Admins"))
  }

  /** Scentry configuration method that disables the internal AuthStore. */
  override protected def configureScentry() = {
    scentry.store = new BlankAuthStore
  }

  // NOTE: Scentry caches authentication and we've turned it off using our BlankAuthStore. But these two methods
  //       still needs to be implemented, so we are implementing something minimum
  protected def fromSession = { case id: String => User(id, "", "", "", verified = false, isAdmin = false, getUtcTimeNow) }
  protected def toSession = { case user: User => user.id }
}
