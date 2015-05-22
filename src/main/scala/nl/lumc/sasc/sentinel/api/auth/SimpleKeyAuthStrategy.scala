package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.language.reflectiveCalls

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import org.scalatra.auth.ScentrySupport

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.api.auth.SimpleKeyAuthStrategy.SimpleKeyAuthRequest
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonErrors, User }

class SimpleKeyAuthStrategy(protected val app: SentinelServlet { def users: UsersAdapter }) extends ScentryStrategy[User] {

  override def name = "SimpleKey"

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] =
    app.users.getUser(app.params("userId")).flatMap {
      case user =>
        if (user.keyMatches(app.params(HeaderApiKey))) Some(user)
        else None
    }
}

object SimpleKeyAuthStrategy {

  class SimpleKeyAuthRequest(req: HttpServletRequest) {

    def providesAuth = Option(req.getHeader(HeaderApiKey)).isDefined

    lazy val userId = Option(req.getAttribute("userId"))
  }

}

trait SimpleKeyAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  private val realm = "Sentinel operations"

  protected def simpleKeyAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    val skReq = new SimpleKeyAuthRequest(request)
    if (skReq.userId.isEmpty) {
      halt(400, CommonErrors.UnspecifiedUserId)
    }
    if (!skReq.providesAuth) {
      response.setHeader("WWW-Authenticate", "ApiKey realm=\"%s\"" format realm)
      halt(401, CommonErrors.Unauthenticated)
    }
    scentry.authenticate("SimpleKey")
  }
}
