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

import org.scalatra.{ Forbidden, ScalatraBase }
import org.scalatra.auth.ScentrySupport

import nl.lumc.sasc.sentinel.api.SentinelServlet
import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ CommonMessages, User }

/**
 * Basic HTTP authentication strategy.
 *
 * Encapsulates the basic HTTP method authentication used by sentinel.
 *
 * Servlets using this authentication strategy must define an attribute `users` which points to a
 * [[nl.lumc.sasc.sentinel.db.UsersAdapter]] instance.
 *
 * @param app [[SentinelServlet]] object using this authentication strategy.
 * @param realm Name of the realm which uses this authentication strategy.
 */
class BasicAuthStrategy(protected override val app: SentinelServlet { def users: UsersAdapter }, realm: String)
    extends org.scalatra.auth.strategy.BasicAuthStrategy[User](app, realm) {

  implicit protected def context: ExecutionContext = ExecutionContext.global

  /** String name of this authentication strategy, used internally by Scentry. */
  override def name = BasicAuthStrategy.name

  /** Checks whether a user's authentication is valid or not. */
  protected def validate(userId: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    val result = app.users.getUser(userId).map { user =>
      for {
        okUser <- user
        if okUser.passwordMatches(password)
      } yield okUser
    }
    Await.result(result, 30.seconds)
  }

  /** Action to be performed after authentication succeeds. */
  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) =
    if (!user.verified) app halt Forbidden(CommonMessages.Unauthorized)

  /** Action to be performed if the authentication fails. */
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app halt (401, CommonMessages.Unauthenticated, headers = Map("WWW-Authenticate" -> challenge))
  }

  /** Retrieves the user ID, given a [[nl.lumc.sasc.sentinel.models.User]] instance */
  protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.id
}

/** Static values for Basic HTTP authentication strategy. */
object BasicAuthStrategy {

  /** String name of the authentication strategy. */
  val name = "Basic"

  /** HTTP WWW-Authenticate challenge issued when authentication fails. */
  val challenge = "Basic realm=\"Sentinel Admins\""
}

/**
 * Basic HTTP authentication trait.
 *
 * This trait is meant to be mixed in with the Scalatra base servlet and the Scentry support trait.
 *
 * @tparam UserType Type representing the user to be authenticated.
 */
trait BasicAuthSupport[UserType <: AnyRef] { this: (ScalatraBase with ScentrySupport[UserType]) =>

  /** Main authentication method used in the controllers for performing Basic HTTP authentication */
  protected def basicAuth()(implicit request: HttpServletRequest, response: HttpServletResponse) = {

    /** Helper function for sending the HTTP response saying authentication is required */
    def askAuth() = {
      response.setHeader("WWW-Authenticate", BasicAuthStrategy.challenge)
      halt(401, CommonMessages.Unauthenticated)
    }
    // Reuse Scalatra's Basic HTTP request wrapper ~ still fits our uses
    val baReq = new org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest(request)
    // Always request authentication if the given authentication is not found or invalid
    if (!baReq.providesAuth || !baReq.isBasicAuth) { askAuth() }
    scentry.authenticate(BasicAuthStrategy.name).getOrElse { askAuth() }
  }
}
