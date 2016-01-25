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
package nl.lumc.sasc.sentinel.exts.pref

import scala.concurrent._

import org.bson.types.ObjectId
import org.json4s.JValue

import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.{ JsonExtractor, MongodbAccessObject, calcMd5 }

/**
 * Example of a simple pipeline runs processor.
 *
 * @param mongo MongoDB access object.
 */
class PrefRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo)
    with JsonExtractor
    with ReferencesAdapter
    with SamplesAdapter {

  /** Run records container. */
  type RunRecord = PrefRunRecord

  /** Sample-level metrics container. */
  type SampleRecord = PrefSampleRecord

  /** Execution context. */
  implicit private def context: ExecutionContext = ExecutionContext.global

  /** Exposed pipeline name. */
  def pipelineName = "pref"

  /** Extracts a reference record from the summary. */
  def extractReference(runJson: JValue): ReferenceRecord = {
    val refJson = runJson \ "reference"
    val contigs = (refJson \ "contigs")
      .extract[Map[String, ReferenceContigRecord]]
      .values.toSeq
    ReferenceRecord(
      combinedMd5 = calcMd5(contigs.map(_.md5).sorted),
      contigs = contigs,
      species = (refJson \ "species").extractOpt[String],
      refName = (refJson \ "name").extractOpt[String])
  }

  /** Sample extractor from JSON. */
  def extractSamples(runJson: JValue, uploaderId: String, refId: ObjectId, runId: ObjectId): Seq[PrefSampleRecord] =
    (runJson \ "samples").extract[Map[String, JValue]].view
      .map {
        case (sampleName, sampleJson) =>
          PrefSampleRecord(
            stats = PrefSampleStats(num = (sampleJson \ "num").extract[Long]),
            uploaderId, runId, refId, Option(sampleName), (runJson \ "runName").extractOpt[String])
      }.toSeq

  /** Uploaded file processor. */
  def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User) = {
    val result = for {
      runJson <- ? <~ extractJson(contents)
      fileId <- ? <~ storeFile(contents, uploader, uploadName)
      runRef <- ? <~ extractReference(runJson)
      ref <- ? <~ getOrCreateReference(runRef)
      samples <- ? <~ extractSamples(runJson, uploader.id, ref.refId, fileId)
      _ <- ? <~ storeSamples(samples)
      run = PrefRunRecord(fileId, uploader.id, pipelineName, samples.map(_.dbId), ref.refId)
      _ <- ? <~ storeRun(run)
    } yield run

    result.run
  }
}
