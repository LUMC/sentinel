package nl.lumc.sasc.sentinel.utils

import org.json4s._

import org.bson.types.ObjectId
import org.json4s.mongo.ObjectIdSerializer

class CustomObjectIdSerializer extends ObjectIdSerializer {

  override def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
    case x: ObjectId => JString(x.toString)
  }
}

