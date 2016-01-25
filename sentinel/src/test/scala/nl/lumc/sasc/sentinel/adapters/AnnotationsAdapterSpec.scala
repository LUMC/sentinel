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

import nl.lumc.sasc.sentinel.models.AnnotationRecord
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class AnnotationsAdapterSpec extends Specification
    with DisjunctionMatchers
    with Mockito {

  /** AnnotationRecord object for testing. */
  private val testAnnotObj = AnnotationRecord(
    annotId = new ObjectId,
    annotMd5 = "2c7041c7986dd97127df35e481ff4c36",
    fileName = Option("annotation.gtf"))

  /** Database record of testing AnnotationRecord object. */
  private val testAnnotDbo = grater[AnnotationRecord].asDBObject(testAnnotObj)

  /** MongoDB testing database name. */
  private val testDbName = "users_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  /** Method to retrieve the underlying collection used by the user adapter. */
  private def getColl(mockDb: Fongo) = mockDb
    .getDB(testDbName)
    .getCollection(MongodbAdapter.CollectionNames.Annotations)

  class TestAnnotationsAdapter(mockDb: Fongo) extends AnnotationsAdapter {

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[AnnotationsAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbAdapter.CollectionNames.Annotations)
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

    val annot1 = testAnnotObj
    val annot2 = AnnotationRecord("1c7041c7986dd97127df35e481ff4c36")
    val annot3 = AnnotationRecord("3c7041c7986dd97127df35e481ff4c36")
    val annots = Seq(annot1, annot3, annot2)

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Annotations) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getAnnotations() must throwA[RuntimeException].await
    }

    "succeed returning an empty seq when the database is empty" in {
      testAdapter.getAnnotations() must beEqualTo(Seq.empty).await
    }

    "succeed returning all annotations (most recent first) when the database is not empty" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        annots.foreach { obj =>
          val dbo = grater[AnnotationRecord].asDBObject(obj)
          coll.insert(dbo)
        }
      }
      adapter.getAnnotations() must beEqualTo(Seq(annot3, annot2, annot1)).await
    }

    "succeed returning N annotations (most recent first) when the database is not empty and maxReturn is set" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        annots.foreach { obj =>
          val dbo = grater[AnnotationRecord].asDBObject(obj)
          coll.insert(dbo)
        }
      }
      adapter.getAnnotations(Some(2)) must beEqualTo(Seq(annot3, annot2)).await
    }
  }

  "getAnnotation" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Annotations) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getAnnotation(testAnnotObj.annotId) must throwA[RuntimeException].await
    }

    "succeed returning None when the database is empty" in {
      testAdapter.getAnnotation(testAnnotObj.annotId) must beNone.await
    }

    "succeed returning None when no records match" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testAnnotDbo) }
      adapter.getAnnotation(new ObjectId) must beNone.await
    }

    "succeed returning the expected object when a matching record exists" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testAnnotDbo) }
      adapter.getAnnotation(testAnnotObj.annotId) must beEqualTo(Some(testAnnotObj)).await
    }
  }

  "getOrCreateAnnotation" should {

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Annotations) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getOrCreateAnnotation(testAnnotObj) must throwA[RuntimeException].await
    }

    "succeed storing the annotation record when it does not exist in the database" in {
      val adapter = testAdapter
      adapter.find(MongoDBObject.empty).count() mustEqual 0
      adapter.getOrCreateAnnotation(testAnnotObj) must beEqualTo(testAnnotObj).await
      adapter.find(testAnnotDbo).count() mustEqual 1
    }

    "succeed retrieving the annotation record without storing anything when it exists in the database" in {
      val adapter = usingAdapter(makeFongo) { coll => coll.insert(testAnnotDbo) }
      val newAnnot = testAnnotObj.copy(annotId = new ObjectId)
      adapter.find(MongoDBObject.empty).count() mustEqual 1
      adapter.getOrCreateAnnotation(newAnnot) must beEqualTo(testAnnotObj).await
      adapter.find(grater[AnnotationRecord].asDBObject(newAnnot)).count() mustEqual 0
      adapter.find(testAnnotDbo).count() mustEqual 1
    }
  }

  "getOrCreateAnnotations" should {

    val annot1 = testAnnotObj
    val annot2 = AnnotationRecord("1c7041c7986dd97127df35e481ff4c36")
    val annot3 = AnnotationRecord("3c7041c7986dd97127df35e481ff4c36")

    "fail when the database operation raises any exception" in {
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.DB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.Annotations) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.getOrCreateAnnotations(Seq(annot1)) must throwA[RuntimeException].await
    }

    "succeed storing the annotation records that do not exist yet and getting the ones that exist" in {
      val adapter = usingAdapter(makeFongo) { coll =>
        coll.insert(testAnnotDbo) // annot1
      }
      val newAnnot1 = annot1.copy(annotId = new ObjectId)
      adapter.find(testAnnotDbo).count() mustEqual 1
      adapter.getOrCreateAnnotations(Seq(annot2, annot3, newAnnot1)) must beEqualTo(Seq(annot2, annot3, annot1)).await
      adapter.find(testAnnotDbo).count() mustEqual 1
      adapter.find(grater[AnnotationRecord].asDBObject(newAnnot1)).count() mustEqual 0
      adapter.find(grater[AnnotationRecord].asDBObject(annot2)).count() mustEqual 1
      adapter.find(grater[AnnotationRecord].asDBObject(annot3)).count() mustEqual 1
    }
  }
}
