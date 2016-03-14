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
package nl.lumc.sasc.sentinel.exts.pann

import org.bson.types.ObjectId
import org.json4s.JValue

import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.{ JsonExtractor, MongodbAccessObject }

import scala.concurrent._

/**
 * Example of a simple pipeline runs processor.
 *
 * @param mongo MongoDB access object.
 */
class PannRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo)
    with JsonExtractor
    with AnnotationsAdapter
    with SamplesAdapter {

  /** Run records container. */
  type RunRecord = PannRunRecord

  /** Sample-level metrics container. */
  type SampleRecord = PannSampleRecord

  /** Execution context. */
  implicit private def context: ExecutionContext = ExecutionContext.global

  /** Exposed pipeline name. */
  def pipelineName = "pann"

  /** Extracts annotation records from the summary. */
  def extractAnnotations(runJson: JValue): Seq[AnnotationRecord] =
    (runJson \ "annotations")
      .children
      .map { fileJson =>
        AnnotationRecord(
          annotMd5 = (fileJson \ "md5").extract[String],
          fileName = (fileJson \ "path").extractOpt[String])
      }

  /** Sample extractor from JSON. */
  def extractSamples(runJson: JValue, uploaderId: String, annotIds: Seq[ObjectId], runId: ObjectId): Seq[PannSampleRecord] =
    (runJson \ "samples").extract[Map[String, JValue]].view
      .map {
        case (sampleName, sampleJson) =>
          PannSampleRecord(
            stats = PannSampleStats(num = (sampleJson \ "num").extract[Long]),
            uploaderId, new ObjectId, runId, annotIds, Option(sampleName), (runJson \ "runName").extractOpt[String])
      }.toSeq

  /** Uploaded file processor. */
  def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User) = {
    val result = for {
      runJson <- ? <~ extractJson(contents)
      fileId <- ? <~ storeFile(contents, uploader, uploadName)
      runAnnots <- ? <~ extractAnnotations(runJson)
      annots <- ? <~ getOrCreateAnnotations(runAnnots)
      samples <- ? <~ extractSamples(runJson, uploader.id, annots.map(_.annotId), fileId)
      _ <- ? <~ storeSamples(samples)
      run = PannRunRecord(fileId, uploader.id, pipelineName, samples.map(_.dbId), annots.map(_.annotId))
      _ <- ? <~ storeRun(run)
    } yield run

    result.run
  }
}
