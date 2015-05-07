package nl.lumc.sasc.sentinel.processors.gentrap

import scala.util.Try

import org.apache.commons.io.FilenameUtils.getExtension
import org.json4s.{ Reader => JsonReader, _ }
import org.json4s.JsonDSL._
import org.json4s.mongo.ObjectIdSerializer
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.db.DatabaseProvider
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunProcessor
import nl.lumc.sasc.sentinel.utils.calcSeqMd5
import nl.lumc.sasc.sentinel.validation.RunValidator

trait GentrapV04Processor extends RunProcessor { this: DatabaseProvider =>

  implicit object GentrapAlignmentStatsReader extends JsonReader[GentrapAlignmentStats] {

    def read(sampleOrLibjson: JValue): GentrapAlignmentStats = {

      val isPaired = (sampleOrLibjson \ "bammetrics" \ "stats" \ "alignment_metrics" \ "PAIR") != JNothing
      val alnMetrics = sampleOrLibjson \ "bammetrics" \ "stats" \ "alignment_metrics" \
        (if (isPaired) "PAIR" else "UNPAIRED")
      val bpFlagstat = sampleOrLibjson \ "bammetrics" \ "stats" \ "biopet_flagstat"
      val insMetrics = sampleOrLibjson \ "bammetrics" \ "stats" \ "insert_size_metrics"
      val rnaMetrics = sampleOrLibjson \ "gentrap" \ "stats" \ "rna_metrics"

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
  }

  implicit object GentrapLibDocumentReader extends JsonReader[GentrapLibDocument] {

    protected def extractReadDocument(libJson: JValue,
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
    def read(libJson: JValue): GentrapLibDocument = GentrapLibDocument(
      name = Option((libJson \ "name").extract[String]),
      rawRead1 = extractReadDocument(libJson, "input_R1", "seqstat_R1", "fastqc_R1"),
      processedRead1 = Try(extractReadDocument(libJson, "output_R1", "seqstat_R1_qc", "fastqc_R1_qc")).toOption,
      rawRead2 = Try(extractReadDocument(libJson, "input_R2", "seqstat_R2", "fastqc_R2")).toOption,
      processedRead2 = Try(extractReadDocument(libJson, "output_R2", "seqstat_R2_qc", "fastqc_R2_qc")).toOption,
      alnStats = libJson.as[GentrapAlignmentStats]
    )
  }

  protected val GentrapAnnotationKeys = Set("annotation_bed", "annotation_refflat", "annotation_gtf")

  implicit val jsonFormats = DefaultFormats + new ObjectIdSerializer

  type SampleDocument = GentrapSampleDocument

  val sampleCollectionName = "gentrap.samples"

  val validator: RunValidator = getSchemaValidator("v0.4/gentrap.json")

  def extractSamples(runJson: JValue, runId: DbId, refId: DbId, annotIds: Seq[DbId]) = {
    (runJson \ "samples")
      .extract[Map[String, JValue]]
      .map {
        case (sampleName, sampleJson) =>
          GentrapSampleDocument(
            name = Option(sampleName),
            runId = runId,
            referenceId = refId,
            annotationIds = annotIds,
            libs = (sampleJson \ "libraries")
              .extract[Map[String, JValue]]
              .map { case (libName, libJson) => (("name", JString(libName)) ++ libJson).as[GentrapLibDocument] }
              .toSeq,
            alnStats = sampleJson.getAs[GentrapAlignmentStats])
      }.toSeq
  }

  def extractReference(runJson: JValue): Reference = {
    val contigMd5s = (runJson \ "gentrap" \ "settings" \ "reference" \\ "md5")
      .children
      .map(_.extract[String])
      .sorted
    val combinedMd5 = calcSeqMd5(contigMd5s)
    Reference(None, contigMd5s, combinedMd5)
  }

  def extractAnnotations(runJson: JValue): Seq[Annotation] = (runJson \ "gentrap" \ "files" \ "pipeline")
    .filterField {
      case JField(key, _) if GentrapAnnotationKeys.contains(key) => true
      case _ => false
    }
    .children
    .map {
      case fileJson =>
        Annotation(None,
          annotMd5 = (fileJson \ "md5").extract[String],
          // Make sure file has extension and always return lower case extensions
          extension = (fileJson \ "path").extractOpt[String] match {
            case None => None
            case Some(path) => {
              val ext = getExtension(path)
              if (ext.isEmpty) None
              else Some(ext.toLowerCase)
            }
          })
    }

}
