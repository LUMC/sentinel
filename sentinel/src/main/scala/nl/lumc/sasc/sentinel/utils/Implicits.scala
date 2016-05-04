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

import scala.util.Try

import com.mongodb.casbah.Imports._
import org.scalatra.servlet.FileItem
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.{ ApiPayload, Payloads, Perhaps, User }, Payloads._

object Implicits {

  /** Implicit class for converting a String to an ObjectId. */
  implicit class ObjectIdFromString(str: String) { def toObjectId: Option[ObjectId] = Try(new ObjectId(str)).toOption }

  /** Implicit class for checking user access to a run database object. */
  implicit class RunRecordDBObject(dbo: DBObject) {

    /** Given a [[User]], checks whether has authorization to access the run record. */
    def authorize(user: User): Perhaps[Unit] = {
      dbo.getAs[String]("uploaderId") match {
        case None => UnexpectedDatabaseError("Run object does not have the required 'uploaderId' key.").left
        case Some(uploaderId) =>
          if (uploaderId == user.id || user.isAdmin) ().right
          else AuthorizationError.left
      }
    }

    /** Shorthand function for accessing the pipeline name of the run. */
    def pipelineName: Perhaps[String] = dbo
      .getAs[String]("pipeline")
      .toRightDisjunction(UnexpectedDatabaseError("Run object does not have the required 'pipeline' key."))

    /** Shorthand function for accessing the sample IDs belonging to the run. */
    def sampleIds: Seq[ObjectId] = dbo.getAsOrElse[Seq[ObjectId]]("sampleIds", Seq())

    /** Shorthand function for accessing the read group IDs belonging to the run. */
    def readGroupIds: Seq[ObjectId] = dbo.getAsOrElse[Seq[ObjectId]]("readGroupIds", Seq())
  }

  /** Implicit class for adding our custom read function to an uploaded file item. */
  implicit class RichFileItem(fi: FileItem) {

    /** Reads the uncompressed contents of the file upload. */
    def readUncompressedBytes(): Array[Byte] =
      nl.lumc.sasc.sentinel.utils.readUncompressedBytes(fi.getInputStream)._1

    /** Reads the contents of the file upload as is. */
    def readBytes(): Array[Byte] = org.scalatra.util.io.readBytes(fi.getInputStream)
  }

  /**
   * Implicit method for making [[nl.lumc.sasc.sentinel.models.ApiPayload]] a monoid instance.
   *
   * This is required so that for-comprehensions using `ApiMessage` as the left disjunction type
   * works. Scalaz requires that type to be a monoid instance so that when we do `filter`, a zero
   * value can be returned.
   *
   * More at: https://groups.google.com/forum/#!topic/scalaz/9SJbGlpS7Kw
   */
  implicit def apiPayloadMonoid = new Monoid[ApiPayload] {
    def zero = ApiPayload("")
    def append(f1: ApiPayload, f2: => ApiPayload) = {
      val f2c = f2 // force computation
      (f1, f2c) match {
        case (m1, m2) if m1.message.isEmpty && m2.message.isEmpty => m1
        case (m1 @ _, m2) if m2.message.isEmpty                   => m1
        case (m1, m2 @ _) if m1.message.isEmpty                   => m2
        case otherwise =>
          val catMsgs = s"${f1.message} | ${f2c.message}"
          val catHnts = f1.hints ++ f2.hints
          if (f1.actionStatusCode == f2.actionStatusCode)
            ApiPayload(catMsgs, catHnts, f1.httpFunc)
          else
            ApiPayload(catMsgs, catHnts)
      }
    }
  }
}
