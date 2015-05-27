package nl.lumc.sasc.sentinel

import scala.util.Try

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.mutable.BeforeAfter

import nl.lumc.sasc.sentinel.db.UsersAdapter
import nl.lumc.sasc.sentinel.models.{ ApiMessage, User }
import nl.lumc.sasc.sentinel.utils.{ CustomObjectIdSerializer, getTimeNow }

trait SentinelServletSpec extends MutableScalatraSpec with EmbeddedMongodbRunner {

  sequential

  override def start() = {
    super[MutableScalatraSpec].start()
    super[EmbeddedMongodbRunner].start()
  }

  override def stop() = {
    super[MutableScalatraSpec].stop()
    super[EmbeddedMongodbRunner].stop()
  }

  implicit val formats = DefaultFormats + new CustomObjectIdSerializer

  def jsonBody: Option[JValue] = Try(parse(body)).toOption

  def apiMessage: Option[ApiMessage] = jsonBody.collect { case json => json.extract[ApiMessage] }

  trait CleanDbContext extends BeforeAfter {
    def before = resetDb()
    def after = resetDb()
  }

  trait UserDbContext extends CleanDbContext with UsersAdapter {

    lazy val mongo = dbAccess

    lazy val user = User("devtest", "d@d.id", "pwd", "key", emailVerified = true, isAdmin = false, getTimeNow)

    override def before = {
      super.before
      addUser(user)
    }
  }

}
