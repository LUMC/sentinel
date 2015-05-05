package nl.lumc.sasc.sentinel.api

import scala.util.Try

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.read
import org.scalatra.test.specs2.ScalatraSpec

trait SentinelSpec { this: ScalatraSpec =>

  implicit val formats = DefaultFormats

  def bodyMap: Option[Map[String, Any]] = Try(read[Map[String, Any]](body)).toOption
}
