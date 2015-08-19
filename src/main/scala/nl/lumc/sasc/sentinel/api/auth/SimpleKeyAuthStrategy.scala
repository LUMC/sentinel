/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.language.reflectiveCalls

import org.scalatra.{ Forbidden, Params, ScalatraBase, Unauthorized }
import org.scalatra.auth.{ ScentryStrategy, ScentrySupport }

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.adapters.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonMessages, User }

/**
 * API key authentication strategy.
 *
 * Encapsulates the API key authentication used by sentinel. This strategy requires the authenticating user to identify
 * him/herself by providing a `userId` parameter in the request URL and an X-SENTINEL-KEY in the HTTP request header.
 *
 * Servlets using this authentication strategy must define an attribute `users` which points to a
 * [[nl.lumc.sasc.sentinel.adapters.UsersAdapter]] instance.
 *
 * @param app [[SentinelServlet]] object using this authentication strategy.
 */
class SimpleKeyAuthStrategy(protected val app: SentinelServlet { def users: UsersAdapter })
    extends ScentryStrategy[User] {

  import SimpleKeyAuthStrategy._

  implicit protected def context: ExecutionContext = ExecutionContext.global

  /** String name of this authentication strategy, used internally by Scentry. */
  override def name = SimpleKeyAuthStrategy.name

  /** Authenticates an incoming HTTP request */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    val result = app.users.getUser(app.params("userId")).map { user =>
      for {
        okUser <- user
        if okUser.keyMatches(request.getHeader(HeaderApiKey))
      } yield okUser
    }

    Await.result(result, 30.seconds)
  }

  /** Action to be performed after authentication succeeds. */
  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) =
    if (!user.verified) app halt Forbidden(CommonMessages.Unauthorized)

  /** Action to be performed if the authentication fails. */
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) =
    app halt Unauthorized(CommonMessages.Unauthenticated, Map("WWW-Authenticate" -> challenge))
}

/** Static values for the API key authentication strategy */
object SimpleKeyAuthStrategy {

  /** String name of the authentication strategy. */
  val name = "SimpleKey"

  /** HTTP WWW-Authenticate challenge issued when authentication fails. */
  val challenge = "SimpleKey realm=\"Sentinel Ops\""
}

/**
 * API key authentication trait.
 *
 * This trait is meant to be mixed in with the Scalatra base servlet and the Scentry support trait.
 *
 * @tparam UserType Type representing the user to be authenticated.
 */
trait SimpleKeyAuthSupport[UserType <: AnyRef] { this: (ScalatraBase with ScentrySupport[UserType]) =>

  /**
   * Main authentication method used in the controllers for performing Basic HTTP authentication.
   *
   * @param f function that accepts a Scalatra Params object and possibly returns a userId string.
   * @return [[nl.lumc.sasc.sentinel.models.User]] object.
   */
  protected def simpleKeyAuth(f: Params => Option[String])(implicit request: HttpServletRequest, response: HttpServletResponse) = {

    /** Helper function for sending the HTTP response saying authentication is required */
    def askAuth() = {
      response.setHeader("WWW-Authenticate", SimpleKeyAuthStrategy.challenge)
      halt(401, CommonMessages.Unauthenticated)
    }
    if (f(params).isEmpty) { halt(400, CommonMessages.UnspecifiedUserId) }
    if (Option(request.getHeader(HeaderApiKey)).isEmpty) { askAuth() }
    scentry.authenticate(SimpleKeyAuthStrategy.name).getOrElse { askAuth() }
  }
}
