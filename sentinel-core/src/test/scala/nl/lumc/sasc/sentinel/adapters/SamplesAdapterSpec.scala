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

import scala.concurrent.duration._

import com.github.fakemongo.Fongo
import com.mongodb.casbah.Imports._
import com.novus.salat._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import nl.lumc.sasc.sentinel.exts.maple.{ MapleSampleRecord, MapleSampleStats }
import nl.lumc.sasc.sentinel.utils.{ MongodbAccessObject, SentinelSalatContext }

class SamplesAdapterSpec extends Specification
    with Mockito { self =>

  /** MongoDB testing database name. */
  private val testDbName = "samples_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  private def pipelineName = "maple"

  /** How many times a Future-based method should retry until we mark it as a failure. */
  private val asyncRetries: Int = 5

  /** How many times a Future-based method should wait until we mark it as a failure. */
  private val asyncWait: FiniteDuration = 1000.millisecond

  class TestSamplesAdapter(mockDb: Fongo) extends SamplesAdapter {

    type SampleRecord = MapleSampleRecord

    val sampleManifest = implicitly[Manifest[SampleRecord]]

    def pipelineName = self.pipelineName

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[SamplesAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbAdapter.CollectionNames.pipelineSamples(pipelineName))
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestSamplesAdapter(mockDb)

  /** Mock runID across all samples. */
  private val runId = new ObjectId

  /** Helper method for creating sample records. */
  private def makeSample(nSnps: Long, uploaderId: String = "tester", runId: ObjectId = runId) =
    MapleSampleRecord(
      stats = MapleSampleStats(nSnps),
      uploaderId = uploaderId,
      dbId = new ObjectId,
      runId = runId)

  /** Sample records to test. */
  private val testSampleObjs = Seq(makeSample(101), makeSample(202), makeSample(303))

  /** Sample database objects to test. */
  private val testSampleDbos = testSampleObjs.map { obj => grater[MapleSampleRecord].asDBObject(obj) }

  "storeSamples" should {

    "fail when the database operation raises any exception" in { implicit ee: ExecutionEnv =>
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.FongoDB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.pipelineSamples(pipelineName)) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.storeSamples(testSampleObjs) must throwA[RuntimeException].await(asyncRetries, asyncWait)
    }

    "succeed storing sample records" in { implicit ee: ExecutionEnv =>
      val adapter = makeAdapter(makeFongo)
      val samplesSize = testSampleObjs.length
      testSampleDbos.map { dbo => adapter.find(dbo).count() } mustEqual Seq.fill(samplesSize)(0)
      adapter.storeSamples(testSampleObjs).map { bw => bw.insertedCount mustEqual samplesSize }.await(asyncRetries, asyncWait)
      testSampleDbos.map { dbo => adapter.find(dbo).count() } mustEqual Seq.fill(samplesSize)(1)
    }
  }
}
