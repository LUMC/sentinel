package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.language.reflectiveCalls

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentrySupport

import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.User

class BasicAuthStrategy(protected override val app: SentinelServlet { def users: UsersAdapter }, realm: String)
    extends org.scalatra.auth.strategy.BasicAuthStrategy[User](app, realm) {

  protected def validate(userId: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] =
    app.users.getUser(userId).flatMap {
      case user =>
        if (user.passwordMatches(password)) Some(user)
        else None
    }

  protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.id
}

trait BasicAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  private val realm = "Sentinel users"

  protected def basicAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    val baReq = new org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest(request)
    if (!baReq.providesAuth) {
      response.setHeader("WWW-Authenticate", "Basic realm=\"%s\"" format realm)
      halt(401, "Unauthenticated")
    }
    if (!baReq.isBasicAuth) {
      halt(400, "Bad Request")
    }
    scentry.authenticate("Basic")
  }
}
