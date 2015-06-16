package nl.lumc.sasc.sentinel.utils

import org.json4s._

import org.bson.types.ObjectId
import org.json4s.mongo.ObjectIdSerializer

/** Custom ObjectId serializer which serializes to and from plain strings entries. */
class CustomObjectIdSerializer extends ObjectIdSerializer {

  private val ObjectIdClass = classOf[ObjectId]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), ObjectId] = {
    case (TypeInfo(ObjectIdClass, _), json) => json match {
      case JString(s) if ObjectId.isValid(s) => new ObjectId(s)
      case JObject(JField("$oid", JString(s)) :: Nil) if ObjectId.isValid(s) => new ObjectId(s)
      case x => throw new MappingException(s"Can't convert $x to ObjectId")
    }
  }

  override def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
    case x: ObjectId => JString(x.toString)
  }
}

