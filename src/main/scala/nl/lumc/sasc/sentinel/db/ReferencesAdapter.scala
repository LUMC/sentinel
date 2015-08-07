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
package nl.lumc.sasc.sentinel.db

import scala.concurrent._

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.ReferenceRecord
import nl.lumc.sasc.sentinel.utils.FutureAdapter

/** Trait for connecting to a reference records collection. */
trait ReferencesAdapter extends MongodbConnector with FutureAdapter {

  /** Execution context for reference database operations. */
  implicit protected def context: ExecutionContext = ExecutionContext.global

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.References)

  /** Private method for getting or creating reference records. */
  private def getOrCreate(ref: ReferenceRecord): ReferenceRecord =
    coll.findOne(MongoDBObject("combinedMd5" -> ref.combinedMd5)) match {
      case Some(dbo) => grater[ReferenceRecord].asObject(dbo)
      case None =>
        coll.insert(grater[ReferenceRecord].asDBObject(ref))
        ref
    }

  /**
   * Stores a reference record in the database or create its copy with the existing database ID if it is already stored.
   *
   * Since reference IDs are generated by Sentinel instead of MongoDB, we always need to check whether the given
   * reference already has a record in the database. This check is done by comparing the `combinedMd5` value of the
   * reference record. If a reference record is already stored in the database, it will be transformed to a new copy
   * that has the database ID. Otherwise, it is added to the database and then returned unchanged.
   *
   * @param ref Reference record.
   * @return Reference record with the existing database ID.
   */
  def getOrCreateReference(ref: ReferenceRecord): Future[ReferenceRecord] = Future { getOrCreate(ref) }

  /**
   * Retrieves all reference records in the database.
   *
   * @return sequence of reference records.
   */
  def getReferences(maxReturn: Option[Int] = None): Future[Seq[ReferenceRecord]] = Future {
    val cursor = coll
      .find()
      .sort(MongoDBObject("creationTimeUtc" -> -1))

    maxReturn match {

      case Some(num) if num > 0 => cursor.limit(num)
        .map { case dbo => grater[ReferenceRecord].asObject(dbo) }.toSeq

      case otherwise => cursor
        .map { case dbo => grater[ReferenceRecord].asObject(dbo) }.toSeq
    }
  }

  /**
   * Retrieves a single reference record.
   *
   * @param refId ID of the reference record to return.
   * @return A reference record object, if it exists.
   */
  def getReference(refId: ObjectId): Future[Option[ReferenceRecord]] = Future {
    coll
      .findOneByID(refId)
      .collect { case dbo => grater[ReferenceRecord].asObject(dbo) }
  }
}
