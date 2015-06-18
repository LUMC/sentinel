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
package nl.lumc.sasc.sentinel.processors.gentrap

import scala.util.Try

import org.apache.commons.io.FilenameUtils.{ getExtension, getName }
import org.bson.types.ObjectId
import org.json4s._
import org.json4s.JsonDSL._
import org.scalatra.servlet.FileItem
import scalaz.{ Failure => _, _ }, Scalaz._

import nl.lumc.sasc.sentinel.Pipeline
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, calcSeqMd5, getUtcTimeNow }
import nl.lumc.sasc.sentinel.utils.implicits._
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

/**
 * Input processor for Gentrap summary file version 0.4.
 *
 * @param mongo MongoDB database access object.
 */
class GentrapV04InputProcessor(protected val mongo: MongodbAccessObject)
    extends SamplesAdapter[GentrapSampleDocument]
    with ValidationAdapter
    with RunsAdapter
    with ReferencesAdapter
    with AnnotationsAdapter {

  /** JSON formats used by this processor. */
  implicit val formats = SentinelJsonFormats

  /** Extracts a reference record from a Gentrap summary. */
  private[processors] def extractReference(runJson: JValue): ReferenceRecord = {
    val contigMd5s = (runJson \ "gentrap" \ "settings" \ "reference" \\ "md5")
      .children
      .map(_.extract[String])
      .sorted
    val combinedMd5 = calcSeqMd5(contigMd5s)
    ReferenceRecord(
      refId = new ObjectId,
      combinedMd5 = combinedMd5,
      contigMd5s = contigMd5s,
      creationTimeUtc = Option(getUtcTimeNow))
  }

  /** Extracts annotation records from a Gentrap summary. */
  private[processors] def extractAnnotations(runJson: JValue): Seq[AnnotationRecord] =
    (runJson \ "gentrap" \ "files" \ "pipeline")
      .filterField {
        case JField(key, _) if GentrapAnnotationKeys.contains(key) => true
        case _ => false
      }
      .children
      .map {
        case fileJson =>
          val filePath = (fileJson \ "path").extractOpt[String]
          AnnotationRecord(
            annotId = new ObjectId,
            annotMd5 = (fileJson \ "md5").extract[String],
            // Make sure file has extension and always return lower case extensions
            extension = filePath match {
              case None => None
              case Some(path) =>
                val ext = getExtension(path)
                if (ext.isEmpty) None
                else Some(ext.toLowerCase)
            },
            fileName = filePath.collect { case path => getName(path) },
            creationTimeUtc = Option(getUtcTimeNow))
      }

  /** Extracts alignment statistics from a sample or library entry in a Gentrap summary. */
  private[processors] def extractAlnStats(effJson: JValue): GentrapAlignmentStats = {

    val isPaired = (effJson \ "bammetrics" \ "stats" \ "alignment_metrics" \ "PAIR") != JNothing
    val alnMetrics = effJson \ "bammetrics" \ "stats" \ "alignment_metrics" \
      (if (isPaired) "PAIR" else "UNPAIRED")
    val bpFlagstat = effJson \ "bammetrics" \ "stats" \ "biopet_flagstat"
    val insMetrics = effJson \ "bammetrics" \ "stats" \ "insert_size_metrics"
    val rnaMetrics = effJson \ "gentrap" \ "stats" \ "rna_metrics"

    GentrapAlignmentStats(
      nReads = (alnMetrics \ "pf_reads").extract[Long],
      nReadsAligned = (alnMetrics \ "pf_reads_aligned").extract[Long],
      nReadsSingleton = isPaired.option { (bpFlagstat \ "MateUnmapped").extract[Long] },
      rateReadsMismatch = (alnMetrics \ "pf_mismatch_rate").extract[Double],
      rateIndel = (alnMetrics \ "pf_indel_rate").extract[Double],
      pctChimeras = isPaired.option { (alnMetrics \ "pct_chimeras").extract[Double] },
      maxInsertSize = (insMetrics \ "max_insert_size").extractOpt[Long],
      medianInsertSize = (insMetrics \ "median_insert_size").extractOpt[Long],
      stdevInsertSize = (insMetrics \ "standard_deviation").extractOpt[Double],
      nBasesAligned = (alnMetrics \ "pf_aligned_bases").extract[Long],
      nBasesUtr = (rnaMetrics \ "utr_bases").extract[Long],
      nBasesCoding = (rnaMetrics \ "coding_bases").extract[Long],
      nBasesIntron = (rnaMetrics \ "intronic_bases").extract[Long],
      nBasesIntergenic = (rnaMetrics \ "intergenic_bases").extract[Long],
      nBasesRibosomal = (rnaMetrics \ "ribosomal_bases").extractOpt[Long],
      median5PrimeBias = (rnaMetrics \ "median_5prime_bias").extract[Double],
      median3PrimeBias = (rnaMetrics \ "median_3prime_bias").extract[Double],
      normalizedTranscriptCoverage = (rnaMetrics \ "normalized_transcript_cov").extract[Seq[Double]])
  }

  /** Extracts an input sequencing file from a library entry in a Gentrap summary. */
  private[processors] def extractReadFile(libJson: JValue, fileKey: String): FileDocument =
    (libJson \ "flexiprep" \ "files" \ "pipeline" \ fileKey).extract[FileDocument]

  /** Extracts a single read statistics from a library entry in a Gentrap summary. */
  private[processors] def extractReadStats(libJson: JValue, seqStatKey: String, fastqcKey: String): ReadStats = {
    val flexStats = libJson \ "flexiprep" \ "stats"
    val nuclCounts = flexStats \ seqStatKey \ "bases" \ "nucleotides"
    ReadStats(
      nBases = (flexStats \ seqStatKey \ "bases" \ "num_total").extract[Long],
      nBasesA = (nuclCounts \ "A").extract[Long],
      nBasesT = (nuclCounts \ "T").extract[Long],
      nBasesG = (nuclCounts \ "G").extract[Long],
      nBasesC = (nuclCounts \ "C").extract[Long],
      nBasesN = (nuclCounts \ "N").extract[Long],
      nBasesByQual = (flexStats \ seqStatKey \ "bases" \ "num_by_qual").extract[Seq[Long]],
      medianQualByPosition = (flexStats \ fastqcKey \ "median_qual_by_position").extract[Seq[Double]],
      nReads = (flexStats \ seqStatKey \ "reads" \ "num_total").extract[Long])
  }

  /** Extracts a library document from a library entry in a Gentrap summary. */
  private[processors] def extractLibDocument(libJson: JValue, uploaderId: String, libName: String,
                                             sampleName: String): GentrapLibDocument = {

    val seqStatsRaw = SeqStats(
      read1 = extractReadStats(libJson, "seqstat_R1", "fastqc_R1"),
      read2 = Try(extractReadStats(libJson, "seqstat_R2", "fastqc_R2")).toOption)

    val seqStatsProcessed = Try(extractReadStats(libJson, "seqstat_R1_qc", "fastqc_R1_qc")).toOption
      .collect {
        case r1proc =>
          SeqStats(read1 = r1proc,
            read2 = Try(extractReadStats(libJson, "seqstat_R2_qc", "fastqc_R2_qc")).toOption)
      }

    val seqFilesRaw = SeqFiles(
      read1 = extractReadFile(libJson, "input_R1"),
      read2 = Try(extractReadFile(libJson, "input_R2")).toOption)

    val seqFilesProcessed = Try(extractReadFile(libJson, "output_R1")).toOption
      .collect {
        case r1f =>
          SeqFiles(read1 = r1f, read2 = Try(extractReadFile(libJson, "output_R2")).toOption)
      }

    GentrapLibDocument(
      libName = Option(libName),
      sampleName = Option(sampleName),
      seqStatsRaw = seqStatsRaw,
      seqStatsProcessed = seqStatsProcessed,
      seqFilesRaw = seqFilesRaw,
      seqFilesProcessed = seqFilesProcessed,
      alnStats = extractAlnStats(libJson))
  }

  /** Extracts a sample document from a sample entry in a Gentrap summary. */
  private[processors] def extractSamples(runJson: JValue, uploaderId: String, runId: ObjectId,
                                         refId: ObjectId, annotIds: Seq[ObjectId]) = {
    (runJson \ "samples")
      .extract[Map[String, JValue]]
      .map {
        case (sampleName, sampleJson) =>
          val libJsons = (sampleJson \ "libraries").extract[Map[String, JValue]]
          GentrapSampleDocument(
            uploaderId = uploaderId,
            sampleName = Option(sampleName),
            runId = runId,
            referenceId = refId,
            annotationIds = annotIds,
            libs = libJsons
              .map { case (libName, libJson) => extractLibDocument(libJson, uploaderId, libName, sampleName) }
              .toSeq,
            // NOTE: Duplication of value in sample level when there is only 1 lib is intended so db queries are simpler
            alnStats =
              if (libJsons.size > 1) extractAlnStats(sampleJson)
              else extractAlnStats(libJsons.values.head))
      }.toSeq
  }

  /** Helper function for creating run records. */
  private[processors] def createRun(fileId: ObjectId, refId: ObjectId, annotIds: Seq[ObjectId],
                                    samples: Seq[GentrapSampleDocument], user: User, pipeline: Pipeline.Value) =
    RunRecord(
      runId = fileId, // NOTE: runId kept intentionally the same as fileId
      refId = Option(refId),
      annotIds = Option(annotIds),
      sampleIds = samples.map(_.id),
      creationTimeUtc = getUtcTimeNow,
      uploaderId = user.id,
      pipeline = pipeline.toString.toLowerCase,
      nSamples = samples.size,
      nLibs = samples.map(_.libs.size).sum)

  /** Attribute keys of Gentrap annotations. */
  protected val GentrapAnnotationKeys = Set("annotation_bed", "annotation_refflat", "annotation_gtf")

  def pipelineName = "gentrap"

  val validator = createValidator("/schemas/biopet/v0.4/gentrap.json")

  def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value): Try[RunRecord] =
    // NOTE: This returns as an all-or-nothing operation, but it may fail midway (the price we pay for using Mongo).
    //       It does not break our application though, so it's an acceptable trade off.
    // TODO: Explore other types that are more expressive than Try to store state.
    for {
      (byteContents, unzipped) <- Try(fi.readInputStream())
      json <- Try(parseAndValidate(byteContents))
      fileId <- Try(storeFile(byteContents, user, pipeline, fi.getName, unzipped))
      runRef <- Try(extractReference(json))
      ref <- Try(getOrStoreReference(runRef))
      refId = ref.refId

      runAnnots <- Try(extractAnnotations(json))
      annots <- Try(storeOrModifyAnnotations(runAnnots))
      annotIds = annots.map(_.annotId)

      samples <- Try(extractSamples(json, user.id, fileId, refId, annotIds))
      _ <- Try(storeSamples(samples))
      run <- Try(createRun(fileId, refId, annotIds, samples, user, pipeline))
      _ <- Try(storeRun(run))
    } yield run
}
