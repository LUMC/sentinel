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
import scala.util.Try

import com.mongodb.casbah.Imports._
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.Payloads._

trait UnitsAdapter extends FutureMongodbAdapter {

  /** Context for Salat conversions. */
  implicit val SalatContext = nl.lumc.sasc.sentinel.utils.SentinelSalatContext

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
  // format: OFF
  protected[adapters] def getUnitDbos(coll: MongoCollection)
                                     (ids: Seq[ObjectId], extraQuery: DBObject = MongoDBObject.empty)
                                     (implicit ec: ExecutionContext): Future[Perhaps[Seq[DBObject]]] = {
    // format: ON
    val idQuery = MongoDBObject("_id" -> MongoDBObject("$in" -> ids))
    val query = Future { coll.find(idQuery ++ extraQuery).toList }
    val nDistinctIds = ids.distinct.length
    query.map { res =>
      if (nDistinctIds > res.length)
        Payloads.UnexpectedDatabaseError(s"Only a portion of unit IDs (${res.length}/$nDistinctIds) can be retrieved.").left
      else if (nDistinctIds < res.length)
        Payloads.UnexpectedDatabaseError(s"Query (${res.length}) returned more unit IDs than requested ($nDistinctIds).").left
      else res.right
    }
  }

  /** Updates an existing database object in the given collection. */
  def updateDbo(coll: MongoCollection)(dbo: DBObject)(implicit ec: ExecutionContext): Future[Perhaps[WriteResult]] = Future {
    for {
      dbId <- dbo._id
        .toRightDisjunction(UnexpectedDatabaseError("Database object missing the required identifier '_id'."))
      wr <- Try(coll.update(MongoDBObject("_id" -> dbId), dbo, upsert = false)).toOption
        .toRightDisjunction(UnexpectedDatabaseError(s"Can not update record ID ${dbId.toString}."))
      res <- if (wr.getN == 1) wr.right else UnexpectedDatabaseError(s"Database update for record ${dbId.toString} failed.").left
    } yield res
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
  def patchDbo(dbo: DBObject, patches: List[JsonPatch.PatchOp])(patchFunc: DboPatchFunction): Perhaps[DBObject] =
    // format: ON
    patches.foldLeft(dbo.right[ApiPayload]) {
      case (recordDbo, patch) =>
        for {
          rdbo <- recordDbo
          patchedDbo <- patchFunc.applyOrElse((rdbo, patch), UnitsAdapter.dboPatchFunctionDefault)
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
  def patchDbos(dbos: Seq[DBObject], patches: List[JsonPatch.PatchOp])(patchFunc: DboPatchFunction): Perhaps[Seq[DBObject]] =
    dbos
      .map(dbo => patchDbo(dbo, patches)(patchFunc)).toList
      .sequence[Perhaps, DBObject]
}

object UnitsAdapter {
  /** Catch-all partial function that is meant to be called when the patch does not match any existing case block. */
  val dboPatchFunctionDefault: DboPatchFunction = {
    case (_: DBObject, p: JsonPatch.PatchOp) => PatchValidationError(p).left
  }
}
