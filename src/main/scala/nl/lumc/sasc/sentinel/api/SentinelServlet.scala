package nl.lumc.sasc.sentinel.api

import org.bson.types.ObjectId
import org.json4s._
import org.json4s.mongo.ObjectIdSerializer

import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

abstract class SentinelServlet extends ScalatraServlet with JacksonJsonSupport {

  protected class SentinelObjectIdSerializer extends ObjectIdSerializer {

    override def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
      case x: ObjectId => JString(x.toString)
    }
  }

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats + new SentinelObjectIdSerializer
}
