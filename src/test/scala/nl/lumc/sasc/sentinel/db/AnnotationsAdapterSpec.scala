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

import com.github.fakemongo.Fongo
import com.mongodb.DBCollection
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.scalaz.DisjunctionMatchers

import nl.lumc.sasc.sentinel.models.AnnotationRecord

class AnnotationsAdapterSpec extends Specification
    with DisjunctionMatchers
    with Mockito {

  /** User object for testing. */
  private val testAnnotObj = AnnotationRecord(
    annotId = new ObjectId,
    annotMd5 = "2c7041c7986dd97127df35e481ff4c36",
    fileName = Option("annotation.gtf"))

  /** MongoDB testing database name. */
  private val testDbName = "users_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  /** Method to retrieve the underlying collection used by the user adapter. */
  private def getColl(mockDb: Fongo) = mockDb
    .getDB(testDbName)
    .getCollection(MongodbConnector.CollectionNames.Annotations)

  class TestAnnotationsAdapter(mockDb: Fongo) extends AnnotationsAdapter {

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[AnnotationsAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbConnector.CollectionNames.Annotations)
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestAnnotationsAdapter(mockDb)

  /** Helper method to create a new adapter instance for each test. */
  private def usingAdapter(fongo: Fongo)(f: DBCollection => Any) = {
    f(getColl(fongo))
    makeAdapter(fongo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def testAdapter = makeAdapter(makeFongo)

  "getAnnotations" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbConnector.CollectionNames.Annotations) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getAnnotations() must throwA[RuntimeException].await
    }

    "succeed returning an empty seq when the database is empty" in {
      testAdapter.getAnnotations() must beEqualTo(Seq.empty).await
    }

    "succeed returning annotations (most recent first) when the database is not empty" in {
      val annot1 = testAnnotObj
      val annot2 = AnnotationRecord("1c7041c7986dd97127df35e481ff4c36")
      val annot3 = AnnotationRecord("3c7041c7986dd97127df35e481ff4c36")
      val annots = Seq(annot1, annot3, annot2)
      val adapter = usingAdapter(makeFongo) { coll =>
        annots.foreach { obj =>
          val dbo = grater[AnnotationRecord].asDBObject(obj)
          coll.insert(dbo)
        }
      }
      adapter.getAnnotations() must beEqualTo(Seq(annot3, annot2, annot1)).await
    }
  }
}

