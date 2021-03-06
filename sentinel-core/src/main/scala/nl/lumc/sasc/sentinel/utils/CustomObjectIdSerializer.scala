/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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
package nl.lumc.sasc.sentinel.utils

import org.json4s._

import org.bson.types.ObjectId
import org.json4s.mongo.ObjectIdSerializer

/**
 * Custom ObjectId serializer for serialization between MongoDB and plain strings.
 *
 * This serializer is required so that `ObjectId`s can be serialized directly to strings instead of JSON objects, i.e.
 * `MongoDBObject("myId" -> ObjectId("1234"))` becomes `{"myId": "1234"}` instead of `{"myId": {"\$oid": "1234"}}`.
 *
 * The (de)serializers are meant to be used by the controllers when sending JSON payloads.
 */
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
