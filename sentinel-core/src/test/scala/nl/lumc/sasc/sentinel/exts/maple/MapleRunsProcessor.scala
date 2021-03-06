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
package nl.lumc.sasc.sentinel.exts.maple

import scala.concurrent._

import org.bson.types.ObjectId
import org.json4s.JValue

import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models.{ ReadGroupLabels, RunLabels, SampleLabels, User }
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.{ ValidatedJsonExtractor, MongodbAccessObject }

/**
 * Example of a simple pipeline runs processor.
 *
 * @param mongo MongoDB access object.
 */
class MapleRunsProcessor(mongo: MongodbAccessObject)
    extends RunsProcessor(mongo)
    with ReadGroupsAdapter
    with ValidatedJsonExtractor {

  /** Exposed pipeline name. */
  def pipelineName = "maple"

  /** JSON schema for incoming summaries. */
  def jsonSchemaUrls = Seq("/schema_examples/maple.json")

  /** Run records container. */
  type RunRecord = MapleRunRecord
  val runManifest = implicitly[Manifest[MapleRunRecord]]

  /** Sample-level metrics container. */
  type SampleRecord = MapleSampleRecord
  val sampleManifest = implicitly[Manifest[MapleSampleRecord]]

  /** Read group-level metrics container. */
  type ReadGroupRecord = MapleReadGroupRecord
  val readGroupManifest = implicitly[Manifest[MapleReadGroupRecord]]

  /** Execution context. */
  implicit private def context: ExecutionContext = ExecutionContext.global

  /** Helper case class for storing records. */
  case class MapleUnits(
    samples: Seq[MapleSampleRecord],
    readGroups: Seq[MapleReadGroupRecord])

  /**
   * Extracts the raw summary JSON into samples and read groups containers.
   *
   * @param runJson Raw run summary JSON.
   * @param uploaderId Username of the uploader.
   * @param runId Database ID for the run record.
   * @return Two sequences: one for sample data and the other for read group data.
   */
  def extractUnits(runJson: JValue, uploaderId: String,
                   runId: ObjectId): MapleUnits = {

    /** Name of the current run. */
    val runName = (runJson \ "run_name").extractOpt[String]

    /** Given the sample name, read group name, and JSON section of the read group, create a read group container. */
    def makeReadGroup(sampleId: ObjectId, sampleName: String, readGroupName: String, readGroupJson: JValue) =
      MapleReadGroupRecord(
        dbId = new ObjectId,
        sampleId = sampleId,
        stats = MapleReadGroupStats(
          nReadsInput = (readGroupJson \ "nReadsInput").extract[Long],
          nReadsAligned = (readGroupJson \ "nReadsAligned").extract[Long]),
        uploaderId = uploaderId,
        runId = runId,
        labels = ReadGroupLabels(runName, Option(sampleName), Option(readGroupName)))

    /** Given the sample name and JSON section of the sample, create a sample container. */
    def makeSample(sampleName: String, sampleJson: JValue) =
      MapleSampleRecord(
        uploaderId = uploaderId,
        runId = runId,
        dbId = new ObjectId,
        labels = SampleLabels(runName, Option(sampleName)),
        stats = MapleSampleStats(nSnps = (sampleJson \ "nSnps").extract[Long]))

    /** Raw sample and read group containers. */
    val parsed = (runJson \ "samples").extract[Map[String, JValue]].view
      .map {
        case (sampleName, sampleJson) =>
          val sample = makeSample(sampleName, sampleJson)
          val readGroups = (sampleJson \ "readGroups").extract[Map[String, JValue]]
            .map { case (readGroupName, readGroupJson) => makeReadGroup(sample.dbId, sampleName, readGroupName, readGroupJson) }
            .toSeq
          (sample, readGroups)
      }.toSeq

    MapleUnits(parsed.map(_._1), parsed.flatMap(_._2))
  }

  /**
   * Validates and stores uploaded run summaries.
   *
   * @param contents Upload contents as a byte array.
   * @param uploadName File name of the upload.
   * @param uploader Uploader of the run summary file.
   * @return A run record of the uploaded run summary file or a list of error messages.
   */
  def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User) = {
    val stack = for {
      // Make sure it is JSON
      runJson <- ? <~ extractAndValidateJson(contents)
      runName = (runJson \ "runName").extractOpt[String]
      // Store the raw file in our database
      fileId <- ? <~ storeFile(contents, uploader, uploadName)
      // Extract samples and read groups
      units <- ? <~ extractUnits(runJson, uploader.id, fileId)
      // Invoke store methods asynchronously
      storeSamplesResult = storeSamples(units.samples)
      storeReadGroupsResult = storeReadGroups(units.readGroups)
      // Check that all store methods are successful
      _ <- ? <~ storeReadGroupsResult
      _ <- ? <~ storeSamplesResult
      // Create run record
      sampleIds = units.samples.map(_.dbId)
      readGroupIds = units.readGroups.map(_.dbId)
      run = MapleRunRecord(fileId, uploader.id, pipelineName, sampleIds, readGroupIds, RunLabels(runName))
      // Store run record into database
      _ <- ? <~ storeRun(run)
    } yield run

    stack.run
  }
}
