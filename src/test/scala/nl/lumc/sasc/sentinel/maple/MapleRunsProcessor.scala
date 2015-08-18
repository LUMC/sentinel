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
package nl.lumc.sasc.sentinel.maple

import scala.concurrent._

import org.bson.types.ObjectId
import org.json4s.JValue
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models.User
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.JsonValidationAdapter

/**
 * Example of a simple pipeline runs processor.
 *
 * @param mongo MongoDB access object.
 */
class MapleRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo)
    with SamplesAdapter
    with ReadGroupsAdapter
    with JsonValidationAdapter {

  /** Run records container. */
  type RunRecord = MapleRunRecord

  /** Sample-level metrics container. */
  type SampleRecord = MapleSampleRecord

  /** Read group-level metrics container. */
  type ReadGroupRecord = MapleReadGroupRecord

  /** Execution context. */
  implicit override protected def context: ExecutionContext = ExecutionContext.global

  /** Exposed pipeline name. */
  def pipelineName = "maple"

  /** Validator object for incoming run summaries. */
  val validator = createValidator("/schema_examples/maple.json")

  /**
   * Extracts the raw summary JSON into samples and read groups containers.
   *
   * @param runJson Raw run summary JSON.
   * @param uploaderId Username of the uploader.
   * @param runId Database ID for the run record.
   * @return Two sequences: one for sample data and the other for read group data.
   */
  def extractUnits(runJson: JValue, uploaderId: String,
                   runId: ObjectId): (Seq[MapleSampleRecord], Seq[MapleReadGroupRecord]) = {

    /** Name of the current run. */
    val runName = (runJson \ "run_name").extractOpt[String]

    /** Given the sample name, read group name, and JSON section of the read group, create a read group container. */
    def makeReadGroup(sampleName: String, readGroupName: String, readGroupJson: JValue) =
      MapleReadGroupRecord(
        stats = MapleReadGroupStats(
          nReadsInput = (readGroupJson \ "nReadsInput").extract[Long],
          nReadsAligned = (readGroupJson \ "nReadsAligned").extract[Long]),
        uploaderId = uploaderId,
        runId = runId,
        readGroupName = Option(readGroupName),
        sampleName = Option(sampleName),
        runName = runName)

    /** Given the sample name and JSON section of the sample, create a sample container. */
    def makeSample(sampleName: String, sampleJson: JValue) =
      MapleSampleRecord(
        stats = MapleSampleStats(nSnps = (sampleJson \ "nSnps").extract[Long]),
        uploaderId, runId, Option(sampleName), runName)

    /** Raw sample and read group containers. */
    val parsed = (runJson \ "samples").extract[Map[String, JValue]].view
      .map {
        case (sampleName, sampleJson) =>
          val sample = makeSample(sampleName, sampleJson)
          val readGroups = (sampleJson \ "readGroups").extract[Map[String, JValue]]
            .map { case (readGroupName, readGroupJson) => makeReadGroup(sampleName, readGroupName, readGroupJson) }
            .toSeq
          (sample, readGroups)
      }.toSeq

    (parsed.map(_._1), parsed.flatMap(_._2))
  }

  /**
   * Validates and stores uploaded run summaries.
   *
   * @param fi Run summary file uploaded via an HTTP endpoint.
   * @param user Uploader of the run summary file.
   * @return A run record of the uploaded run summary file.
   */
  def processRun(fi: FileItem, user: User) = {
    for {
      // Read input stream and checks whether the uploaded file is unzipped or not
      (byteContents, unzipped) <- Future { fi.readInputStream() }
      // Make sure it is JSON
      runJson <- Future { parseAndValidate(byteContents) }
      // Store the raw file in our database
      fileId <- Future { storeFile(byteContents, user, fi.getName, unzipped) }
      // Extract run, samples, and read groups
      (samples, readGroups) <- Future { extractUnits(runJson, user.id, fileId) }
      // Store samples
      _ <- storeSamples(samples)
      // Store read groups
      _ <- storeReadGroups(readGroups)
      // Create run record
      run = MapleRunRecord(fileId, user.id, pipelineName, samples.map(_.dbId), readGroups.map(_.dbId))
      // Store run record into database
      _ <- Future { storeRun(run) }
    } yield run
  }
}