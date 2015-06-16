package nl.lumc.sasc.sentinel.api.auth

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import org.scalatra.auth.ScentryAuthStore.ScentryAuthStore

/** Scentry authentication store implementation that does not store any session / cookie information. */
private[auth] class BlankAuthStore extends ScentryAuthStore {
  def get(implicit request: HttpServletRequest, response: HttpServletResponse): String = ""
  def set(value: String)(implicit request: HttpServletRequest, response: HttpServletResponse) = {}
  def invalidate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {}
}
