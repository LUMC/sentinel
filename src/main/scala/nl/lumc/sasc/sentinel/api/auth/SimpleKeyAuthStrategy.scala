package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.language.reflectiveCalls

import org.scalatra.{ Forbidden, Params, ScalatraBase, Unauthorized }
import org.scalatra.auth.{ ScentryStrategy, ScentrySupport }

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonErrors, User }

class SimpleKeyAuthStrategy(protected val app: SentinelServlet { def users: UsersAdapter })
    extends ScentryStrategy[User] {

  import SimpleKeyAuthStrategy._

  override def name = SimpleKeyAuthStrategy.name

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] =
    app.users.getUser(app.params("userId")).flatMap {
      case user =>
        if (user.keyMatches(request.getHeader(HeaderApiKey))) Some(user)
        else None
    }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) =
    if (!user.verified) app halt Forbidden(CommonErrors.Unauthorized)

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) =
    app halt Unauthorized(CommonErrors.Unauthenticated, Map("WWW-Authenticate" -> challenge))
}

object SimpleKeyAuthStrategy {

  val name = "SimpleKey"

  val challenge = "SimpleKey realm=\"Sentinel Ops\""
}

trait SimpleKeyAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  protected def simpleKeyAuth(f: Params => Option[String])(implicit request: HttpServletRequest, response: HttpServletResponse) = {

    def askAuth() = {
      response.setHeader("WWW-Authenticate", SimpleKeyAuthStrategy.challenge)
      halt(401, CommonErrors.Unauthenticated)
    }
    if (f(params).isEmpty) { halt(400, CommonErrors.UnspecifiedUserId) }
    if (Option(request.getHeader(HeaderApiKey)).isEmpty) { askAuth() }
    scentry.authenticate(SimpleKeyAuthStrategy.name).getOrElse { askAuth() }
  }
}
