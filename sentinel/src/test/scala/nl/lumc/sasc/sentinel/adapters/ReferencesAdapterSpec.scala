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

import com.github.fakemongo.Fongo
import com.mongodb.DBCollection
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.scalaz.DisjunctionMatchers

import nl.lumc.sasc.sentinel.models.{ ReferenceRecord, ReferenceContigRecord }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class ReferencesAdapterSpec extends Specification
    with DisjunctionMatchers
    with Mockito {

  /** ReferenceRecord object for testing. */
  private val testRefObj = ReferenceRecord(
    contigs = Seq(ReferenceContigRecord("md51", 100), ReferenceContigRecord("md52", 200)),
    combinedMd5 = "md5C",
    refName = Option("ref"),
    species = Option("species"))

  /** Database record of testing ReferenceRecord object. */
  private val testRefDbo = grater[ReferenceRecord].asDBObject(testRefObj)

  /** MongoDB testing database name. */
  private val testDbName = "users_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  /** Method to retrieve the underlying collection used by the user adapter. */
  private def getColl(mockDb: Fongo) = mockDb
    .getDB(testDbName)
    .getCollection(MongodbAdapter.CollectionNames.References)

  class TestReferencesAdapter(mockDb: Fongo) extends ReferencesAdapter {

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[ReferencesAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbAdapter.CollectionNames.References)
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestReferencesAdapter(mockDb)

  /** Helper method to create a new adapter instance for each test. */
  private def usingAdapter(fongo: Fongo)(f: DBCollection => Any) = {
    f(getColl(fongo))
    makeAdapter(fongo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def testAdapter = makeAdapter(makeFongo)

  "getReference" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.References) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getReference(testRefObj.refId) must throwA[RuntimeException].await
    }

    "succeed returning None when the database is empty" in {
      testAdapter.getReference(testRefObj.refId) must beNone.await
    }

    "succeed returning None when no records match" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testRefDbo) }
      adapter.getReference(new ObjectId) must beNone.await
    }

    "succeed returning the expected object when a matching record exists" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testRefDbo) }
      adapter.getReference(testRefObj.refId) must beEqualTo(Some(testRefObj)).await
    }
  }

  "getOrCreateReference" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.References) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getOrCreateReference(testRefObj) must throwA[RuntimeException].await
    }

    "succeed storing the reference record when it does not exist in the database" in {
      val adapter = testAdapter
      adapter.find(MongoDBObject.empty).count() mustEqual 0
      adapter.getOrCreateReference(testRefObj) must beEqualTo(testRefObj).await
      adapter.find(testRefDbo).count() mustEqual 1
    }

    "succeed retrieving the reference record without storing anything when it exists in the database" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testRefDbo) }
      val newRef = testRefObj.copy(refId = new ObjectId)
      adapter.find(MongoDBObject.empty).count() mustEqual 1
      adapter.getOrCreateReference(newRef) must beEqualTo(testRefObj).await
      adapter.find(grater[ReferenceRecord].asDBObject(newRef)).count() mustEqual 0
      adapter.find(testRefDbo).count() mustEqual 1
    }
  }
}
