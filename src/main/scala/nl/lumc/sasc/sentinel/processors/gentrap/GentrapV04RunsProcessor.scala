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

import scala.concurrent._
import scala.util.Try

import org.apache.commons.io.FilenameUtils.getName
import org.bson.types.ObjectId
import org.json4s._
import org.json4s.JsonDSL._
import org.scalatra.servlet.FileItem
import scalaz.{ Failure => _, _ }, Scalaz._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.utils.{ calcMd5, getUtcTimeNow, JsonValidationAdapter }
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Input processor for Gentrap summary file version 0.4.
 *
 * @param mongo MongoDB database access object.
 */
class GentrapV04RunsProcessor(mongo: MongodbAccessObject)
    extends RunsProcessor(mongo)
    with UnitsAdapter[GentrapSampleRecord, GentrapReadGroupRecord]
    with JsonValidationAdapter
    with ReferencesAdapter
    with AnnotationsAdapter {

  /** Execution context. */
  implicit override protected def context: ExecutionContext = ExecutionContext.global

  /** Extracts a reference record from a Gentrap summary. */
  private[processors] def extractReference(runJson: JValue): ReferenceRecord = {
    val refJson = runJson \ "gentrap" \ "settings" \ "reference"
    val contigs = (refJson \ "contigs")
      .extract[Map[String, ReferenceContigRecord]]
      .values.toSeq
    ReferenceRecord(
      combinedMd5 = calcMd5(contigs.map(_.md5).sorted),
      contigs = contigs,
      species = (refJson \ "species").extractOpt[String],
      refName = (refJson \ "name").extractOpt[String])
  }

  /** Extracts annotation records from a Gentrap summary. */
  private[processors] def extractAnnotations(runJson: JValue): Seq[AnnotationRecord] =
    (runJson \ "gentrap" \ "files" \ "pipeline")
      .filterField {
        case JField(key, _) if GentrapAnnotationKeys.contains(key) => true
        case _ => false
      }
      .children
      .map { fileJson =>
        AnnotationRecord(
          annotMd5 = (fileJson \ "md5").extract[String],
          fileName = (fileJson \ "path").extractOpt[String].map { path => getName(path) })
      }

  /** Extracts alignment statistics from a sample or read group entry in a Gentrap summary. */
  private[processors] def extractAlnStats(effJson: JValue): GentrapAlignmentStats = {

    val isPaired = (effJson \ "bammetrics" \ "stats" \ "CollectAlignmentSummaryMetrics" \ "PAIR") != JNothing
    val alnMetrics = effJson \ "bammetrics" \ "stats" \ "CollectAlignmentSummaryMetrics" \
      (if (isPaired) "PAIR" else "UNPAIRED")
    val bpFlagstat = effJson \ "bammetrics" \ "stats" \ "biopet_flagstat"
    val insMetrics = effJson \ "bammetrics" \ "stats" \ "CollectInsertSizeMetrics" \ "metrics"
    val rnaMetrics = effJson \ "bammetrics" \ "stats" \ "rna" \ "metrics"
    val rnaHisto = effJson \ "bammetrics" \ "stats" \ "rna" \ "histogram"

    // Use BiopetFlagstat values for fallback is CollectAlignmentSummaryMetrics's values for these are 0.
    // The values are 0 when CAS is not run without setting the reference flag.
    val nReadsTotal = (alnMetrics \ "PF_READS").extract[Long] match {
      case 0         => (bpFlagstat \ "All").extract[Long]
      case otherwise => otherwise
    }
    val nReadsAligned = (alnMetrics \ "PF_READS_ALIGNED").extract[Long] match {
      case 0         => (bpFlagstat \ "Mapped").extract[Long]
      case otherwise => otherwise
    }

    GentrapAlignmentStats(
      nReadsTotal = nReadsTotal,
      nReadsAligned = nReadsAligned,
      nReadsSingleton = isPaired.option { (bpFlagstat \ "MateUnmapped").extract[Long] },
      nReadsProperPair = isPaired.option { (bpFlagstat \ "ProperPair").extract[Long] },
      rateReadsMismatch = (alnMetrics \ "PF_MISMATCH_RATE").extract[Double],
      rateIndel = (alnMetrics \ "PF_INDEL_RATE").extract[Double],
      pctChimeras = isPaired.option { (alnMetrics \ "PCT_CHIMERAS").extract[Double] },
      maxInsertSize = (insMetrics \ "MAX_INSERT_SIZE").extractOpt[Long],
      medianInsertSize = (insMetrics \ "MEDIAN_INSERT_SIZE").extractOpt[Long],
      stdevInsertSize = (insMetrics \ "STANDARD_DEVIATION").extractOpt[Double],
      nBasesAligned = (rnaMetrics \ "PF_ALIGNED_BASES").extract[Long],
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

  /** Extracts an input sequencing file from a read group entry in a Gentrap summary. */
  private[processors] def extractReadFile(readGroupJson: JValue, fileKey: String): FileRecord =
    (readGroupJson \ "flexiprep" \ "files" \ "pipeline" \ fileKey).extract[FileRecord]

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
      .flatMap { case (key, value) => value.map(v => PerBaseStat(key, v)) }
      // pair with index
      .zipWithIndex
      // and return only items where the base position match the index
      // (so we only take consecutive stats from the first position onwards
      .takeWhile { case (PerBaseStat(actualIdx, value), expectedIdx) => actualIdx == expectedIdx }
      .map { case (PerBaseStat(_, value), _) => value }

  /** Extracts a single read statistics from a read group entry in a Gentrap summary. */
  private[processors] def extractReadStats(readGroupJson: JValue, seqStatKey: String, fastqcKey: String): ReadStats = {
    val flexStats = readGroupJson \ "flexiprep" \ "stats"
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

  /** Extracts a read group document from a read group entry in a Gentrap summary. */
  private[processors] def extractReadGroupDocument(readGroupJson: JValue, uploaderId: String, runId: ObjectId, refId: ObjectId,
                                                   annotIds: Seq[ObjectId], readGroupName: String, sampleName: String,
                                                   runName: Option[String] = None): GentrapReadGroupRecord = {

    val seqStatsRaw = SeqStats(
      read1 = extractReadStats(readGroupJson, "seqstat_R1", "fastqc_R1"),
      read2 = Try(extractReadStats(readGroupJson, "seqstat_R2", "fastqc_R2")).toOption)

    val seqStatsProcessed = Try(extractReadStats(readGroupJson, "seqstat_R1_qc", "fastqc_R1_qc")).toOption
      .map { r1proc =>
        SeqStats(read1 = r1proc, read2 = Try(extractReadStats(readGroupJson, "seqstat_R2_qc", "fastqc_R2_qc")).toOption)
      }

    val seqFilesRaw = SeqFiles(
      read1 = extractReadFile(readGroupJson, "input_R1"),
      read2 = Try(extractReadFile(readGroupJson, "input_R2")).toOption)

    val seqFilesProcessed = Try(extractReadFile(readGroupJson, "output_R1")).toOption
      .map { r1f => SeqFiles(read1 = r1f, read2 = Try(extractReadFile(readGroupJson, "output_R2")).toOption) }

    GentrapReadGroupRecord(
      alnStats = extractAlnStats(readGroupJson),
      seqStatsRaw = seqStatsRaw,
      seqStatsProcessed = seqStatsProcessed,
      seqFilesRaw = seqFilesRaw,
      seqFilesProcessed = seqFilesProcessed,
      referenceId = refId,
      annotationIds = annotIds,
      isPaired = seqStatsRaw.read2.isDefined,
      readGroupName = Option(readGroupName),
      sampleName = Option(sampleName),
      runId = runId,
      uploaderId = uploaderId,
      runName = runName)
  }

  /** Extracts samples and read groups from a Gentrap summary. */
  private[processors] def extractUnits(runJson: JValue, uploaderId: String, runId: ObjectId,
                                       refId: ObjectId, annotIds: Seq[ObjectId], runName: Option[String] = None) = {
    val parsed = (runJson \ "samples")
      .extract[Map[String, JValue]]
      .view
      .map {
        case (sampleName, sampleJson) =>
          val readGroupJsons = (sampleJson \ "libraries").extract[Map[String, JValue]]
          val gSample = GentrapSampleRecord(
            uploaderId = uploaderId,
            sampleName = Option(sampleName),
            runId = runId,
            runName = runName,
            referenceId = refId,
            annotationIds = annotIds,
            // NOTE: Duplication of value in sample level when there is only 1 RG is intended so db queries are simpler
            alnStats = readGroupJsons.toList match {
              case (readGroupName, readGroupJson) :: Nil => extractAlnStats(readGroupJson)
              case otherwise                             => extractAlnStats(sampleJson)
            })
          val gLibs = readGroupJsons
            .map {
              case (readGroupName, readGroupJson) =>
                extractReadGroupDocument(readGroupJson, uploaderId, runId, refId, annotIds, readGroupName, sampleName,
                  runName)
            }
            .toSeq
          (gSample, gLibs)
      }.toSeq
    (parsed.map(_._1), parsed.flatMap(_._2))
  }

  /** Helper function for creating run records. */
  private[processors] def createRun(fileId: ObjectId, refId: ObjectId, annotIds: Seq[ObjectId],
                                    samples: Seq[GentrapSampleRecord], readGroups: Seq[GentrapReadGroupRecord],
                                    user: User, runName: Option[String] = None) =
    RunRecord(
      runId = fileId, // NOTE: runId kept intentionally the same as fileId
      refId = Option(refId),
      annotIds = Option(annotIds),
      runName = runName,
      sampleIds = samples.map(_.dbId),
      readGroupIds = readGroups.map(_.dbId),
      creationTimeUtc = getUtcTimeNow,
      uploaderId = user.id,
      pipeline = pipelineName)

  /** Attribute keys of Gentrap annotations. */
  protected val GentrapAnnotationKeys = Set("annotation_bed", "annotation_refflat", "annotation_gtf")

  def pipelineName = "gentrap"

  val validator = createValidator("/schemas/biopet/v0.4/gentrap.json")

  def processRun(fi: FileItem, user: User): Future[RunRecord] =
    // NOTE: This returns as an all-or-nothing operation, but it may fail midway (the price we pay for using Mongo).
    //       It does not break our application though, so it's an acceptable trade off.
    // TODO: Explore other types that are more expressive than Try to store state.
    for {
      (byteContents, unzipped) <- Future { fi.readInputStream() }
      runJson <- Future { parseAndValidate(byteContents) }
      fileId <- Future { storeFile(byteContents, user, fi.getName, unzipped) }
      runRef <- Future { extractReference(runJson) }
      ref <- getOrCreateReference(runRef)
      refId = ref.refId

      runAnnots <- Future { extractAnnotations(runJson) }
      annots <- getOrCreateAnnotations(runAnnots)
      annotIds = annots.map(_.annotId)

      runName = (runJson \ "meta" \ "run_name").extractOpt[String]
      (samples, readGroups) <- Future { extractUnits(runJson, user.id, fileId, refId, annotIds, runName) }
      _ <- Future { storeSamples(samples) }
      _ <- Future { storeReadGroups(readGroups) }

      run = createRun(fileId, refId, annotIds, samples, readGroups, user, runName)
      _ <- Future { storeRun(run) }
    } yield run
}
