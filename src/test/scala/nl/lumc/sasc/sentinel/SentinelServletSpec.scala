package nl.lumc.sasc.sentinel

import scala.util.Try

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.test.specs2.MutableScalatraSpec

import nl.lumc.sasc.sentinel.models.ApiMessage
import nl.lumc.sasc.sentinel.utils.CustomObjectIdSerializer

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
}
