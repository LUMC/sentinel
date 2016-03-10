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

import nl.lumc.sasc.sentinel.utils.FutureMixin

trait UnitsAdapter extends MongodbAdapter
    with FutureMixin {

  /** Type alias for partial functions for performing database object patching. */
  type PatchFunc = PartialFunction[(DBObject, SinglePathPatch), Perhaps[DBObject]]

  /** Updates an existing database object in the database. */
  def updateDbo(coll: MongoCollection, dbo: DBObject)(implicit ec: ExecutionContext): Future[Perhaps[WriteResult]] = Future {
    dbo._id match {
      case None => UnexpectedDatabaseError("Database object missing the required identifier '_id'.").left
      case Some(dbid) =>
        val wr = coll
          .update(MongoDBObject("_id" -> dbid), dbo, upsert = false)
        if (wr.getN == 1) wr.right
        else UnexpectedDatabaseError("Run record update failed.").left
    }
  }

  /**
   * Patches an existing database object.
   *
   * @param runDbo MongoDB object to apply the patch to.
   * @param patches Patch operations.
   * @param patchFunc Partial functions for performing the patch.
   * @return Either a sequence of error messages or the patched run record object.
   */
  // format: OFF
  def patchDbo(runDbo: DBObject, patches: List[SinglePathPatch])(patchFunc: PatchFunc): Perhaps[DBObject] =
    // format: ON
    patches.foldLeft(runDbo.right[ApiPayload]) {
      case (recordDbo, patch) =>
        for {
          dbo <- recordDbo
          patchedDbo <- patchFunc((dbo, patch))
        } yield patchedDbo
    }

}
