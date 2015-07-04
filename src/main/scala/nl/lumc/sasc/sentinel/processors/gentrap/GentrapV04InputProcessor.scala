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
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, calcMd5, getUtcTimeNow }
import nl.lumc.sasc.sentinel.utils.implicits._
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

/**
 * Input processor for Gentrap summary file version 0.4.
 *
 * @param mongo MongoDB database access object.
 */
class GentrapV04InputProcessor(protected val mongo: MongodbAccessObject)
    extends UnitsAdapter[GentrapSampleDocument, GentrapLibDocument]
    with ValidationAdapter
    with RunsAdapter
    with ReferencesAdapter
    with AnnotationsAdapter {

  /** JSON formats used by this processor. */
  implicit val formats = SentinelJsonFormats

  /** Extracts a reference record from a Gentrap summary. */
  private[processors] def extractReference(runJson: JValue): ReferenceRecord = {
    val refJson = runJson \ "gentrap" \ "settings" \ "reference"
    val contigs = (refJson \ "contigs")
      .extract[Map[String, ReferenceContigRecord]]
      .values.toSeq
    ReferenceRecord(
      refId = new ObjectId,
      combinedMd5 = calcMd5(contigs.map(_.md5).sorted),
      contigs = contigs,
      species = (refJson \ "species").extractOpt[String],
      refName = (refJson \ "name").extractOpt[String],
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

    val isPaired = (effJson \ "bammetrics" \ "stats" \ "CollectAlignmentSummaryMetrics" \ "PAIR") != JNothing
    val alnMetrics = effJson \ "bammetrics" \ "stats" \ "CollectAlignmentSummaryMetrics" \
      (if (isPaired) "PAIR" else "UNPAIRED")
    val bpFlagstat = effJson \ "bammetrics" \ "stats" \ "biopet_flagstat"
    val insMetrics = effJson \ "bammetrics" \ "stats" \ "CollectInsertSizeMetrics" \ "metrics"
    val rnaMetrics = effJson \ "bammetrics" \ "stats" \ "rna" \ "metrics"
    val rnaHisto = effJson \ "bammetrics" \ "stats" \ "rna" \ "histogram"

    GentrapAlignmentStats(
      nReadsTotal = (alnMetrics \ "PF_READS").extract[Long],
      nReadsAligned = (alnMetrics \ "PF_READS_ALIGNED").extract[Long],
      nReadsSingleton = isPaired.option { (bpFlagstat \ "MateUnmapped").extract[Long] },
      nReadsProperPair = isPaired.option { (bpFlagstat \ "ProperPair").extract[Long] },
      rateReadsMismatch = (alnMetrics \ "PF_MISMATCH_RATE").extract[Double],
      rateIndel = (alnMetrics \ "PF_INDEL_RATE").extract[Double],
      pctChimeras = isPaired.option { (alnMetrics \ "PCT_CHIMERAS").extract[Double] },
      maxInsertSize = (insMetrics \ "MAX_INSERT_SIZE").extractOpt[Long],
      medianInsertSize = (insMetrics \ "MEDIAN_INSERT_SIZE").extractOpt[Long],
      stdevInsertSize = (insMetrics \ "STANDARD_DEVIATION").extractOpt[Double],
      nBasesAligned = (alnMetrics \ "PF_ALIGNED_BASES").extract[Long],
      nBasesUtr = (rnaMetrics \ "UTR_BASES").extract[Long],
      nBasesCoding = (rnaMetrics \ "CODING_BASES").extract[Long],
      nBasesIntron = (rnaMetrics \ "INTRONIC_BASES").extract[Long],
      nBasesIntergenic = (rnaMetrics \ "INTERGENIC_BASES").extract[Long],
      nBasesRibosomal = (rnaMetrics \ "RIBOSOMAL_BASES").extractOpt[Long],
      median5PrimeBias = (rnaMetrics \ "MEDIAN_5PRIME_BIAS").extract[Double],
      median3PrimeBias = (rnaMetrics \ "MEDIAN_3PRIME_BIAS").extract[Double],
      median5PrimeTo3PrimeBias = (rnaMetrics \ "MEDIAN_5PRIME_TO_3PRIME_BIAS").extractOpt[Double],
      normalizedTranscriptCoverage = (rnaHisto \ "All_Reads.normalized_coverage").extract[Seq[Double]])
  }

  /** Extracts an input sequencing file from a library entry in a Gentrap summary. */
  private[processors] def extractReadFile(libJson: JValue, fileKey: String): FileDocument =
    (libJson \ "flexiprep" \ "files" \ "pipeline" \ fileKey).extract[FileDocument]

  /** Case class for containing per-base position statistics. */
  private[processors] case class PerBaseStat[T](index: Int, value: T)

  /** Extracts FastQC module statistics which are spread out per base position or per group of base positions. */
  private[processors] def extractFastqcStats(fastqcJson: JValue, fastqcModuleName: String,
                                             statPerPositionName: String): Seq[Double] =
    (fastqcJson \ fastqcModuleName)
      .extract[Map[String, Map[String, Double]]].view
      // filter for keys which are single base positions (not range)
      .filter { case (key, value) => Try(key.toInt).toOption.isDefined }.toSeq
      // get the statistics on the position and turn the base position to 0-based indexing
      .map { case (key, value) => (key.toInt - 1, value.get(statPerPositionName)) }
      // sort on the base position
      .sortBy(_._1)
      // take only while the numbers are consecutive
      .takeWhile { case (key, value) => value.isDefined }
      // get the stats value
      .map { case (key, value) => PerBaseStat(key, value.get) }
      // pair with index
      .zipWithIndex
      // and return only items where the base position match the index
      // (so we only take consecutive stats from the first position onwards
      .takeWhile { case (PerBaseStat(actualIdx, value), expectedIdx) => actualIdx == expectedIdx }
      .map { case (PerBaseStat(_, value), _) => value }

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
      nBasesByQual = (flexStats \ seqStatKey \ "bases" \ "num_qual").extract[Seq[Long]],
      medianQualByPosition = extractFastqcStats(flexStats \ fastqcKey, "per_base_sequence_quality", "median"),
      nReads = (flexStats \ seqStatKey \ "reads" \ "num_total").extract[Long])
  }

  /** Extracts a library document from a library entry in a Gentrap summary. */
  private[processors] def extractLibDocument(libJson: JValue, uploaderId: String, runId: ObjectId, refId: ObjectId,
                                             annotIds: Seq[ObjectId], libName: String, sampleName: String,
                                             runName: Option[String] = None): GentrapLibDocument = {

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
      alnStats = extractAlnStats(libJson),
      seqStatsRaw = seqStatsRaw,
      seqStatsProcessed = seqStatsProcessed,
      seqFilesRaw = seqFilesRaw,
      seqFilesProcessed = seqFilesProcessed,
      referenceId = refId,
      annotationIds = annotIds,
      isPaired = seqStatsRaw.read2.isDefined,
      libName = Option(libName),
      sampleName = Option(sampleName),
      runId = runId,
      uploaderId = uploaderId,
      runName = runName)
  }

  /** Extracts samples and libraries from a Gentrap summary. */
  private[processors] def extractUnits(runJson: JValue, uploaderId: String, runId: ObjectId,
                                       refId: ObjectId, annotIds: Seq[ObjectId], runName: Option[String] = None) = {
    val parsed = (runJson \ "samples")
      .extract[Map[String, JValue]]
      .view
      .map {
        case (sampleName, sampleJson) =>
          val libJsons = (sampleJson \ "libraries").extract[Map[String, JValue]]
          val gSample = GentrapSampleDocument(
            uploaderId = uploaderId,
            sampleName = Option(sampleName),
            runId = runId,
            runName = runName,
            referenceId = refId,
            annotationIds = annotIds,
            // NOTE: Duplication of value in sample level when there is only 1 lib is intended so db queries are simpler
            alnStats =
              if (libJsons.size > 1) extractAlnStats(sampleJson)
              else extractAlnStats(libJsons.values.head))
          val gLibs = libJsons
            .map {
              case (libName, libJson) =>
                extractLibDocument(libJson, uploaderId, runId, refId, annotIds, libName, sampleName, runName)
            }
            .toSeq
          (gSample, gLibs)
      }.toSeq
    (parsed.map(_._1), parsed.map(_._2).flatten)
  }

  /** Helper function for creating run records. */
  private[processors] def createRun(fileId: ObjectId, refId: ObjectId, annotIds: Seq[ObjectId],
                                    samples: Seq[GentrapSampleDocument], libs: Seq[GentrapLibDocument],
                                    user: User, pipeline: Pipeline.Value, runName: Option[String] = None) =
    RunRecord(
      runId = fileId, // NOTE: runId kept intentionally the same as fileId
      refId = Option(refId),
      annotIds = Option(annotIds),
      runName = runName,
      sampleIds = samples.map(_.id),
      libIds = libs.map(_.id),
      creationTimeUtc = getUtcTimeNow,
      uploaderId = user.id,
      pipeline = pipeline.toString.toLowerCase,
      nSamples = samples.size,
      nLibs = libs.size)

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
      runJson <- Try(parseAndValidate(byteContents))
      fileId <- Try(storeFile(byteContents, user, pipeline, fi.getName, unzipped))
      runRef <- Try(extractReference(runJson))
      ref <- Try(getOrStoreReference(runRef))
      refId = ref.refId

      runAnnots <- Try(extractAnnotations(runJson))
      annots <- Try(storeOrModifyAnnotations(runAnnots))
      annotIds = annots.map(_.annotId)

      runName = (runJson \ "meta" \ "run_name").extractOpt[String]
      (samples, libs) <- Try(extractUnits(runJson, user.id, fileId, refId, annotIds, runName))
      _ <- Try(storeSamples(samples))
      _ <- Try(storeLibs(libs))
      run <- Try(createRun(fileId, refId, annotIds, samples, libs, user, pipeline, runName))
      _ <- Try(storeRun(run))
    } yield run
}
