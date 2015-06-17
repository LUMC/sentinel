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

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._

import nl.lumc.sasc.sentinel.models.AnnotationRecord

/** Trait for connecting to an annotation records collection. */
trait AnnotationsAdapter extends MongodbConnector {

  /** Collection used by this adapter. */
  private lazy val coll = mongo.db(collectionNames.Annotations)

  /**
   * Stores annotation records in the database or create copies with existing database IDs if they are already stored.
   *
   * Since annotation IDs are generated by Sentinel instead of MongoDB, we always need to check whether the given
   * annotation already has a record in the database. This check is done by comparing the `annotMd5` value of the
   * annotation record. If an annotation record is already stored in the database, it will be transformed to a new copy
   * that has the database ID. Otherwise, it is added to the database and then returned unchanged.
   *
   * The store or modify operation affects each annotation records separately. In a given sequence, it is possible
   * to have some annotations stored and others modified.
   *
   * @param annots Annotation records to store or retrieve.
   * @return Annotation records with existing database IDs.
   */
  def storeOrModifyAnnotations(annots: Seq[AnnotationRecord]): Seq[AnnotationRecord] =
    // TODO: refactor to use Futures instead
    annots
      .map {
        case annot => coll.findOne(MongoDBObject("annotMd5" -> annot.annotMd5)) match {
          case Some(dbo) => annot.copy(annotId = dbo._id.get)
          case None =>
            coll.insert(grater[AnnotationRecord].asDBObject(annot))
            annot
        }
      }

  /**
   * Retrieves all annotation records in the database.
   *
   * @return Annotation records.
   */
  def getAnnotations(): Seq[AnnotationRecord] =
    // TODO: refactor to use Futures instead
    coll
      .find()
      .sort(MongoDBObject("creationTimeUtc" -> -1))
      .map { case dbo => grater[AnnotationRecord].asObject(dbo) }
      .toSeq

  /**
   * Retrieves a single annotation record.
   *
   * @param annotId ID of the annotation record to return.
   * @return An annotation record object, if it exists.
   */
  def getAnnotation(annotId: ObjectId): Option[AnnotationRecord] =
    // TODO: refactor to use Futures instead
    coll
      .findOneByID(annotId)
      .collect { case dbo => grater[AnnotationRecord].asObject(dbo) }
}
