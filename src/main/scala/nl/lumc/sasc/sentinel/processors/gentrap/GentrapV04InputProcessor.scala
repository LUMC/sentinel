package nl.lumc.sasc.sentinel.processors.gentrap

import java.io.ByteArrayInputStream
import java.time.Clock
import java.util.Date

import scala.util.{ Failure, Success, Try }

import org.apache.commons.io.FilenameUtils.{ getExtension, getName }
import org.bson.types.ObjectId
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.mongo.ObjectIdSerializer
import org.scalatra.servlet.FileItem
import scalaz.{ Failure => _, _ }, Scalaz._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.SentinelProcessor
import nl.lumc.sasc.sentinel.utils.{ calcSeqMd5, getByteArray }
import nl.lumc.sasc.sentinel.validation.ValidationAdapter

class GentrapV04InputProcessor(protected val mongo: MongodbAccessObject)
    extends SentinelProcessor
    with SamplesAdapter
    with ValidationAdapter
    with RunsAdapter
    with ReferencesAdapter
    with AnnotationsAdapter
    with MongodbConnector {

  implicit val jsonFormats = DefaultFormats + new ObjectIdSerializer

  private def extractAlnStats(effJson: JValue): GentrapAlignmentStats = {

    val isPaired = (effJson \ "bammetrics" \ "stats" \ "alignment_metrics" \ "PAIR") != JNothing
    val alnMetrics = effJson \ "bammetrics" \ "stats" \ "alignment_metrics" \
      (if (isPaired) "PAIR" else "UNPAIRED")
    val bpFlagstat = effJson \ "bammetrics" \ "stats" \ "biopet_flagstat"
    val insMetrics = effJson \ "bammetrics" \ "stats" \ "insert_size_metrics"
    val rnaMetrics = effJson \ "gentrap" \ "stats" \ "rna_metrics"

    GentrapAlignmentStats(
      nReads = (alnMetrics \ "pf_reads").extract[Long],
      nReadsAligned = (alnMetrics \ "pf_reads_aligned").extract[Long],
      rateReadsMismatch = (alnMetrics \ "pf_mismatch_rate").extract[Double],
      rateIndel = (alnMetrics \ "pf_indel_rate").extract[Double],
      pctChimeras = isPaired.option { (alnMetrics \ "pct_chimeras").extract[Double] },
      nSingletons = isPaired.option { (bpFlagstat \ "MateUnmapped").extract[Long] },
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

  private def extractReadDocument(libJson: JValue,
                                  fileKey: String, seqStatKey: String, fastqcKey: String): GentrapReadDocument = {

    val flexStats = libJson \ "flexiprep" \ "stats"
    val nuclCounts = flexStats \ seqStatKey \ "bases" \ "nucleotides"

    GentrapReadDocument(
      file = (libJson \ "flexiprep" \ "files" \ "pipeline" \ fileKey).extract[FileDocument],
      stats = ReadStats(
        nBases = (flexStats \ seqStatKey \ "bases" \ "num_total").extract[Long],
        nBasesA = (nuclCounts \ "A").extract[Long],
        nBasesT = (nuclCounts \ "T").extract[Long],
        nBasesG = (nuclCounts \ "G").extract[Long],
        nBasesC = (nuclCounts \ "C").extract[Long],
        nBasesN = (nuclCounts \ "N").extract[Long],
        nBasesByQual = (flexStats \ seqStatKey \ "bases" \ "num_by_qual").extract[Seq[Long]],
        medianQualByPosition = (flexStats \ fastqcKey \ "median_qual_by_position").extract[Seq[Double]],
        nReads = (flexStats \ seqStatKey \ "reads" \ "num_total").extract[Long])
    )
  }

  private def extractLibDocument(name: String, libJson: JValue): GentrapLibDocument =
    GentrapLibDocument(
      name = (libJson \ "name").extractOpt[String],
      rawSeq = GentrapSeqDocument(
        read1 = extractReadDocument(libJson, "input_R1", "seqstat_R1", "fastqc_R1"),
        read2 = Try(extractReadDocument(libJson, "input_R2", "seqstat_R2", "fastqc_R2")).toOption),
      processedSeq =
        Try(extractReadDocument(libJson, "output_R1", "seqstat_R1_qc", "fastqc_R1_qc")).toOption
          .collect {
            case ps =>
              GentrapSeqDocument(
                read1 = ps,
                read2 = Try(extractReadDocument(libJson, "output_R2", "seqstat_R2_qc", "fastqc_R2_qc")).toOption)
          },
      alnStats = extractAlnStats(libJson))

  type SampleDocument = GentrapSampleDocument

  val samplesCollectionName = GentrapSamplesCollectionName

  val validator = createValidator("/schemas/biopet/v0.4/gentrap.json")

  protected val GentrapAnnotationKeys = Set("annotation_bed", "annotation_refflat", "annotation_gtf")

  def extractSamples(runJson: JValue, runId: ObjectId, refId: ObjectId, annotIds: Seq[ObjectId]) = {
    (runJson \ "samples")
      .extract[Map[String, JValue]]
      .map {
        case (sampleName, sampleJson) =>
          val libJsons = (sampleJson \ "libraries").extract[Map[String, JValue]]
          GentrapSampleDocument(
            name = Option(sampleName),
            runId = runId,
            referenceId = refId,
            annotationIds = annotIds,
            libs = libJsons
              .map { case (libName, libJson) => extractLibDocument(libName, libJson) }
              .toSeq,
            // NOTE: Duplication of value in sample level when there is only 1 lib is intended so db queries are simpler
            alnStats =
              if (libJsons.size > 1) extractAlnStats(sampleJson)
              else extractAlnStats(libJsons.values.head))
      }.toSeq
  }

  def extractReference(runJson: JValue): Reference = {
    val contigMd5s = (runJson \ "gentrap" \ "settings" \ "reference" \\ "md5")
      .children
      .map(_.extract[String])
      .sorted
    val combinedMd5 = calcSeqMd5(contigMd5s)
    Reference(
      refId = new ObjectId,
      combinedMd5 = combinedMd5,
      contigMd5s = contigMd5s,
      creationTime = Option(Date.from(Clock.systemUTC().instant)))
  }

  def extractAnnotations(runJson: JValue): Seq[Annotation] = (runJson \ "gentrap" \ "files" \ "pipeline")
    .filterField {
      case JField(key, _) if GentrapAnnotationKeys.contains(key) => true
      case _ => false
    }
    .children
    .map {
      case fileJson =>
        val filePath = (fileJson \ "path").extractOpt[String]
        Annotation(
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
          creationTime = Option(Date.from(Clock.systemUTC().instant)))
    }

  def createRun(fileId: ObjectId, refId: ObjectId, annotIds: Seq[ObjectId], samples: Seq[SampleDocument],
                userId: String, pipeline: String) =
    RunDocument(
      runId = fileId, // NOTE: runId kept intentionally the same as fileId
      refId = Option(refId),
      annotIds = Option(annotIds),
      creationTime = Date.from(Clock.systemUTC().instant),
      uploader = userId,
      pipeline = pipeline,
      nSamples = samples.size,
      nLibs = samples.map(_.libs.size).sum)

  def processRun(fi: FileItem, userId: String, pipeline: String): Try[RunDocument] =
    // NOTE: This returns as an all-or-nothing operation, but it may fail midway (the price we pay for using Mongo).
    //       It does not break our application though, so it's an acceptable trade off.
    // TODO: Explore other types that are more expressive than Try to store state.
    for {
      (byteContents, unzipped) <- Try(fi.readInputStream())
      json <- Try(parseAndValidate(byteContents))
      fileId <- Try(storeFile(byteContents, userId, pipeline, fi.getName, unzipped))
      runRef <- Try(extractReference(json))
      ref <- Try(getOrStoreReference(runRef))
      refId = ref.refId

      runAnnots <- Try(extractAnnotations(json))
      annots <- Try(getOrStoreAnnotations(runAnnots))
      annotIds = annots.map(_.annotId)

      samples <- Try(extractSamples(json, fileId, refId, annotIds))
      _ <- Try(storeSamples(samples))
      run <- Try(createRun(fileId, refId, annotIds, samples, userId, pipeline))
      _ <- Try(storeRun(run))
    } yield run
}
