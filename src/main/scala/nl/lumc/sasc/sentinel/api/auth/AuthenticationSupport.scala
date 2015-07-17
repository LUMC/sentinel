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

import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }

import org.scalatra.auth.ScentryAuthStore.ScentryAuthStore

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
    scentry.store = new ScentryAuthStore {
      def get(implicit request: HttpServletRequest, response: HttpServletResponse): String = ""
      def set(value: String)(implicit request: HttpServletRequest, response: HttpServletResponse) = ()
      def invalidate()(implicit request: HttpServletRequest, response: HttpServletResponse) = ()
    }
  }

  // NOTE: Scentry caches authentication and we've turned it off using our ad-hoc ScentryAuthStore. But these two methods
  //       still needs to be implemented, so we are implementing something minimum
  protected def fromSession = { case id: String => User(id, "", "", "", verified = false, isAdmin = false, getUtcTimeNow) }
  protected def toSession = { case user: User => user.id }
}
