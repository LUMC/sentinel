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
package nl.lumc.sasc.sentinel.utils

import com.mongodb.casbah.Imports._

import nl.lumc.sasc.sentinel.LibType
import nl.lumc.sasc.sentinel.models.FragmentStatsLike

trait Selector {

  def attrName: String

  def query: MongoDBObject

  def negatedQuery: MongoDBObject = query.get(attrName) match {
    case None    => MongoDBObject.empty
    case Some(q) => MongoDBObject(attrName -> MongoDBObject("$not" -> q))
  }
}

object Selector {

  def combine(operator: String)(f: Selector => MongoDBObject)(selectors: Selector*): MongoDBObject = {
    val s = selectors.map { f }.filter(_.nonEmpty)
    if (s.isEmpty) MongoDBObject.empty
    else MongoDBObject(operator -> s)
  }

  def combineAnd(selectors: Selector*) = combine("$and")((s: Selector) => s.query)(selectors: _*)

  def fromLibType(lt: Option[LibType.Value]) = lt.map(_ == LibType.Paired) match {
    case None    => EmptySelector
    case Some(t) => OneMatchOne(FragmentStatsLike.pairAttrib, t)
  }
}

object EmptySelector extends Selector {
  def attrName = ""
  override def query = MongoDBObject.empty
}

case class OneMatchOne[T](attrName: String, queryValue: T) extends Selector {
  def query = MongoDBObject(attrName -> MongoDBObject("$eq" -> queryValue))
}

case class OneOptMatchOne[T](attrName: String, queryValue: Option[T]) extends Selector {
  def query = queryValue match {
    case None    => MongoDBObject.empty
    case Some(v) => MongoDBObject(attrName -> MongoDBObject("$eq" -> v))
  }
}

case class OneInMany[T](attrName: String, queryValue: T) extends Selector {
  def query = MongoDBObject(attrName -> MongoDBObject("$elemMatch" -> MongoDBObject("$eq" -> queryValue)))
}

case class OneOptInMany[T](attrName: String, queryValue: Option[T]) extends Selector {
  def query = queryValue match {
    case None    => MongoDBObject.empty
    case Some(v) => MongoDBObject(attrName -> MongoDBObject("$elemMatch" -> MongoDBObject("$eq" -> v)))
  }
}

case class ManyContainOne[T](attrName: String, queryValues: Seq[T]) extends Selector {
  def query =
    if (queryValues.isEmpty) MongoDBObject.empty
    else MongoDBObject(attrName -> MongoDBObject("$in" -> queryValues))
}

case class ManyIntersectMany[T](attrName: String, queryValues: Seq[T]) extends Selector {
  def query =
    if (queryValues.isEmpty) MongoDBObject.empty
    else MongoDBObject(attrName -> MongoDBObject("$elemMatch" -> MongoDBObject("$in" -> queryValues)))
}

