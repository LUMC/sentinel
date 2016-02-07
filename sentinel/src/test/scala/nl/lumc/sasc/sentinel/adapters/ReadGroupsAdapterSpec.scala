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
import com.novus.salat.global._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import nl.lumc.sasc.sentinel.exts.maple.{ MapleSampleRecord, MapleReadGroupRecord, MapleReadGroupStats }
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject

class ReadGroupsAdapterSpec extends Specification
    with Mockito { self =>

  sequential

  /** MongoDB testing database name. */
  private val testDbName = "readGroups_test"

  /** In-memory MongoDB database instance. */
  private def makeFongo = new Fongo("mem")

  private def pipelineName = "maple"

  /** How many times a Future-based method should retry until we mark it as a failure. */
  private val asyncRetries: Int = 5

  /** How many times a Future-based method should wait until we mark it as a failure. */
  private val asyncWait: FiniteDuration = 1000.millisecond

  class TestReadGroupsAdapter(mockDb: Fongo) extends ReadGroupsAdapter {

    type ReadGroupRecord = MapleReadGroupRecord

    type SampleRecord = MapleSampleRecord

    def pipelineName = self.pipelineName

    val mongo = MongodbAccessObject.fromJava(mockDb.getMongo, testDbName)

    private[ReadGroupsAdapterSpec] def find(dbo: MongoDBObject) = mockDb
      .getDB(testDbName)
      .getCollection(MongodbAdapter.CollectionNames.pipelineReadGroups(pipelineName))
      .find(dbo)
  }

  /** Helper method to create a new adapter instance for each test. */
  private def makeAdapter(mockDb: Fongo) = new TestReadGroupsAdapter(mockDb)

  /** Mock runID across all readGroups. */
  private val runId = new ObjectId

  /** Helper method for creating sample records. */
  private def makeReadGroup(nReadsInput: Long, nReadsAligned: Long,
                            uploaderId: String = "tester", runId: ObjectId = runId) =
    MapleReadGroupRecord(MapleReadGroupStats(nReadsInput, nReadsAligned), uploaderId, runId)

  /** ReadGroup records to test. */
  private val testReadGroupObjs = Seq(makeReadGroup(100, 80), makeReadGroup(150, 120), makeReadGroup(200, 160))

  /** ReadGroup database objects to test. */
  private val testReadGroupDbos = testReadGroupObjs.map { obj => grater[MapleReadGroupRecord].asDBObject(obj) }

  "storeReadGroups" should {

    "fail when the database operation raises any exception" in { implicit ee: ExecutionEnv =>
      val mockFongo = mock[Fongo]
      val mockDb = mock[com.mongodb.FongoDB]
      mockFongo.getDB(testDbName) returns mockDb
      mockDb.getCollection(MongodbAdapter.CollectionNames.pipelineReadGroups(pipelineName)) throws new RuntimeException
      val adapter = makeAdapter(mockFongo)
      adapter.storeReadGroups(testReadGroupObjs) must throwA[RuntimeException].await(asyncRetries, asyncWait)
    }

    "succeed storing sample records" in { implicit ee: ExecutionEnv =>
      val adapter = makeAdapter(makeFongo)
      val readGroupsSize = testReadGroupObjs.length
      testReadGroupDbos.map { dbo => adapter.find(dbo).count() } mustEqual Seq.fill(readGroupsSize)(0)
      adapter.storeReadGroups(testReadGroupObjs)
        .map { bw => bw.insertedCount mustEqual readGroupsSize }.await(asyncRetries, asyncWait)
      testReadGroupDbos.map { dbo => adapter.find(dbo).count() } mustEqual Seq.fill(readGroupsSize)(1)
    }
  }
}