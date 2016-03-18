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
package nl.lumc.sasc.sentinel.adapters

import scala.concurrent.{ ExecutionContext, Future }

import com.mongodb.casbah.Imports._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.Payloads._

trait UnitsAdapter extends FutureMongodbAdapter {

  /**
   * Retrieves the raw database object of the given unit IDs.
   *
   * If any of the given unit ID is invalid / not present in the database, an
   * [[nl.lumc.sasc.sentinel.models.ApiPayload]] object containing the error message is returned instead.
   *
   * Note that since the input requires a set of IDs, the order of the returned database objects is undefined.
   *
   * @param coll [[MongoCollection]] on which the query will be performed
   * @param ids IDs of the records to retrieve.
   * @param extraQuery Additional query for further selection of the raw database objects.
   * @return Sequence of raw database objects or an API payload containing an error message.
   */
  protected[adapters] def getUnitRecordsDbo(coll: MongoCollection)(ids: Set[ObjectId],
                                                                   extraQuery: DBObject = MongoDBObject.empty): Perhaps[Seq[DBObject]] = {
    val idQuery = MongoDBObject("_id" -> MongoDBObject("$in" -> ids))
    val res = coll.find(idQuery ++ extraQuery).toSeq
    if (ids.size == res.length) res.right
    else Payloads.UnexpectedDatabaseError("Not all sample IDs can be retrieved.").left
  }

  /** Updates an existing database object in the given collection. */
  def updateDbo(coll: MongoCollection)(dbo: DBObject)(implicit ec: ExecutionContext): Future[Perhaps[WriteResult]] = Future {
    dbo._id match {
      case None => UnexpectedDatabaseError("Database object missing the required identifier '_id'.").left
      case Some(dbid) =>
        val wr = coll
          .update(MongoDBObject("_id" -> dbid), dbo, upsert = false)
        if (wr.getN == 1) wr.right
        else UnexpectedDatabaseError("Database object update failed.").left
    }
  }

  /**
   * Patches an existing database object.
   *
   * @param dbo MongoDB object to apply the patch to.
   * @param patches Patch operations.
   * @param patchFunc Partial functions for performing the patch.
   * @return Either an [[ApiPayload]] or the patched run record object.
   */
  // format: OFF
  def patchDbo(dbo: DBObject, patches: List[SinglePathPatch])(patchFunc: DboPatchFunc): Perhaps[DBObject] =
    // format: ON
    patches.foldLeft(dbo.right[ApiPayload]) {
      case (recordDbo, patch) =>
        for {
          rdbo <- recordDbo
          patchedDbo <- patchFunc.applyOrElse((rdbo, patch), UnitsAdapter.dboPatchFuncBaseCase)
        } yield patchedDbo
    }

  /**
   * Patches multiple existing database objects.
   *
   * @param dbos Sequence of MongoDB objects to apply to.
   * @param patches Patch operations.
   * @param patchFunc Partial functions for performing the patch.
   * @return Either an [[ApiPayload]] or the patched run record objects.
   */
  def patchDbos(dbos: Seq[DBObject], patches: List[SinglePathPatch])(patchFunc: DboPatchFunc): Perhaps[Seq[DBObject]] = {

    val patchedDbos = dbos
      .map(dbo => patchDbo(dbo, patches)(patchFunc))

    val oks = patchedDbos
      .collect { case \/-(s) => s }

    // If the number of successful patches is the same as the number of inputs, that means we're good.
    if (oks.length == dbos.length) oks.right
    // Otherwise we accumulate all the errors.
    else patchedDbos
      .collect { case -\/(f) => f }
      .reduceLeft { _ |+| _ }
      .left
  }
}

object UnitsAdapter {
  /** Catch-all partial function that is meant to be called when the patch does not match any existing case block. */
  val dboPatchFuncBaseCase: DboPatchFunc = { case (_, p) => PatchValidationError(p).left }
}
