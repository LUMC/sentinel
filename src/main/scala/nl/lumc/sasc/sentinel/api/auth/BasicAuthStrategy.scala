package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.language.reflectiveCalls

import org.scalatra.{ Forbidden, ScalatraBase }
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

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app halt (401, CommonErrors.Unauthenticated, headers = Map("WWW-Authenticate" -> challenge))
  }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) =
    if (!user.verified) app halt Forbidden(CommonErrors.Unauthorized)

  protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.id
}

object BasicAuthStrategy {
  val name = "Basic"
  val challenge = "Basic realm=\"Sentinel Admins\""
}

trait BasicAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  protected def basicAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {

    def askAuth() = {
      response.setHeader("WWW-Authenticate", BasicAuthStrategy.challenge)
      halt(401, CommonErrors.Unauthenticated)
    }
    val baReq = new org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest(request)
    if (!baReq.providesAuth || !baReq.isBasicAuth) { askAuth() }
    scentry.authenticate(BasicAuthStrategy.name).getOrElse { askAuth() }
  }
}
