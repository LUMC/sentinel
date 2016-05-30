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
import scala.math.BigInt
import org.bson.types.ObjectId
import org.json4s.JValue

import scalaz._, Scalaz._
import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.Payloads.JsonValidationError
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.{ JsonExtractor, MongodbAccessObject }

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
  val runManifest = implicitly[Manifest[PrefRunRecord]]

  /** Sample-level metrics container. */
  type SampleRecord = PrefSampleRecord
  val sampleManifest = implicitly[Manifest[PrefSampleRecord]]

  /** Execution context. */
  implicit private def context: ExecutionContext = ExecutionContext.global

  /** Exposed pipeline name. */
  def pipelineName = "pref"

  /** Extracts a reference record from the summary. */
  def extractReference(runJson: JValue): Perhaps[ReferenceRecord] = {
    val refJson = runJson \ "reference"
    val contigs = (refJson \ "contigs")
      .extract[Map[String, Map[String, Any]]].toList
      .traverse[Option, ReferenceSequenceRecord] { case (k, v) =>
        for {
          md5sum <- v.get("md5") match {
            case res @ Some(iv: String) => Option(iv)
            case otherwise              => None
          }
          length <- v.get("length") match {
            case res @ Some(iv: Long)   => Option(iv)
            case res @ Some(iv: Int)    => Option(iv.toLong)
            case res @ Some(iv: BigInt) =>
              if (iv.isValidLong) Option(iv.toLong)
              else None
            case otherwise              => None
          }
        } yield ReferenceSequenceRecord(k, length, md5sum)
      }
    contigs match {
      case None    => JsonValidationError("One or more reference sequence(s) is unparseable.").left
      case Some(v) =>
        ReferenceRecord(contigs = v, refName = (refJson \ "name").extractOpt[String]).right
    }
  }

  /** Sample extractor from JSON. */
  def extractSamples(runJson: JValue, uploaderId: String, refId: ObjectId, runId: ObjectId): Seq[PrefSampleRecord] =
    (runJson \ "samples").extract[Map[String, JValue]].view
      .map {
        case (sampleName, sampleJson) =>
          PrefSampleRecord(
            stats = PrefSampleStats(num = (sampleJson \ "num").extract[Long]),
            uploaderId, new ObjectId, runId, refId,
            SampleLabels(Option(sampleName), (runJson \ "runName").extractOpt[String]))
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
