package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import org.scalatra.servlet.RichRequest
import org.scalatra.util.RicherString

import scala.language.reflectiveCalls

import org.scalatra.{ ScalatraBase, Unauthorized }
import org.scalatra.auth.ScentryStrategy
import org.scalatra.auth.ScentrySupport

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.api.auth.SimpleKeyAuthStrategy.SimpleKeyAuthRequest
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonErrors, User }

class SimpleKeyAuthStrategy(protected val app: SentinelServlet { def users: UsersAdapter }, realm: String)
    extends ScentryStrategy[User] {

  override def name = "SimpleKey"

  protected def challenge = "ApiKey realm=\"%s\"" format realm

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] =
    app.users.getUser(app.params("userId")).flatMap {
      case user =>
        if (user.keyMatches(request.getHeader(HeaderApiKey))) Some(user)
        else None
    }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app halt Unauthorized(CommonErrors.Unauthenticated, Map("WWW-Authenticate" -> challenge))
  }
}

object SimpleKeyAuthStrategy {

  class SimpleKeyAuthRequest(req: RichRequest) {

    def providesAuth = req.header(HeaderApiKey).isDefined

    lazy val userId = new RicherString(req.parameters("userId")).blankOption
  }

}

trait SimpleKeyAuthSupport[UserType <: AnyRef] {
  this: (ScalatraBase with ScentrySupport[UserType]) =>

  private val realm = "Sentinel operations"

  protected def simpleKeyAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    val skReq = new SimpleKeyAuthRequest(new RichRequest(request))
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
