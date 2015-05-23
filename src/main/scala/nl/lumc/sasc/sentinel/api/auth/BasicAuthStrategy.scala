package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.language.reflectiveCalls

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentrySupport

import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonErrors, User }

class BasicAuthStrategy(protected override val app: SentinelServlet { def users: UsersAdapter }, realm: String)
    extends org.scalatra.auth.strategy.BasicAuthStrategy[User](app, realm) {

  override def name = BasicAuthStrategy.name

  protected def validate(userId: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] =
    app.users.getUser(userId).flatMap {
      case user =>
        if (user.passwordMatches(password)) Some(user)
        else None
    }

  protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.id
}

object BasicAuthStrategy {
  val name = "Basic"
  val challenge = "Basic realm=\"Sentinel Admins\""
}

trait BasicAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  protected def basicAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    val baReq = new org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest(request)
    if (!baReq.providesAuth) {
      response.setHeader("WWW-Authenticate", BasicAuthStrategy.challenge)
      halt(401, CommonErrors.Unauthenticated)
    }
    if (!baReq.isBasicAuth) {
      halt(400, CommonErrors.IncorrectAuthMode)
    }
    scentry.authenticate(BasicAuthStrategy.name)
  }
}
