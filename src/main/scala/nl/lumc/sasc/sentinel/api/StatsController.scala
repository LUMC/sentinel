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
package nl.lumc.sasc.sentinel.api

import scala.util.Try

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel._
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.GenericRunsProcessor
import nl.lumc.sasc.sentinel.processors.gentrap._
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Controller for the `/stats` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class StatsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with AuthenticationSupport { self =>

  /** Controller name, shown in the generated swagger spec */
  override protected val applicationName: Option[String] = Some("stats")

  /** Controller description, shown in the generated Swagger spec */
  protected val applicationDescription: String = "Statistics of deposited run summaries"

  /** Adapter for connecting to the run collections */
  protected val runs = new GenericRunsProcessor(mongo)

  /** Adapter for connecting to the gentrap collection */
  protected val gentrap = new GentrapStatsProcessor(mongo)

  /** Adapter for connecting to the users collection */
  protected val users = new UsersAdapter { val mongo = self.mongo }

  options("/runs") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/gentrap/alignments") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/gentrap/alignments/aggregate") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/gentrap/sequences") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  options("/gentrap/sequences/aggregate") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD")
  }

  // format: OFF
  val statsRunsGetOperation = (apiOperation[Seq[PipelineStats]]("statsRunsGet")
    summary "Retrieves general statistics of uploaded run summaries.")
  // format: ON

  get("/runs", operation(statsRunsGetOperation)) {
    logger.info(requestLog)
    Ok(runs.getGlobalRunStats())
  }

  // format: OFF
  val statsGentrapAlignmentsGetOperation = (apiOperation[Seq[GentrapAlignmentStats]]("statsGentrapAlignmentsGet")
    summary "Retrieves the alignment statistics of Gentrap pipeline runs."
    notes
      """This endpoint returns a list containing alignment-level metrics of Gentrap pipeline runs.
        |
        |By default:
        |
        | * Each data point represents metrics from an alignment file of a sample. To return alignment metrics from
        |   single libraries, use the `accLevel` parameter.
        |
        | * When the `accLevel` parameter is set to `lib`, the returned data points represent either single-end or
        |   paired-end sequencing files. To return data points from only one library type, use the `libType` parameter.
        |   When the `accLevel` is set to `sample`, the `libType` parameter is ignored as a single sample alignment
        |   may be a mix of single-end and paired-end library.
        |
        | * Data points are returned in random order which changes in every query. To maintain a sorted order
        |   (most-recently created first), use the `sorted` parameter.
        |
        | * All data points of all Gentrap runs are returned. The filter for specific data points, use the `runIds`,
        |   `refIds`, and/or `annotIds` parameter.
        |
        | * All data points are unlabeled. To label the data points with their respective IDs and names, you must be
        |   the data points' uploader and authenticate yourself using your API key. If the returned data points contain
        |   data points you did not upload, they will remain unlabeled.
        |
        |Each returned data point has the following metrics:
        |
        | * `maxInsertSize`: Maximum insert size (only for paired-end libraries).
        | * `median3PrimeBias`: Median value of 3' coverage biases from the top 1000 expressed transcripts (3'-most 100 bp).
        | * `median5PrimeBias`: Median value of 5' coverage biases from the top 1000 expressed transcripts (5'-most 100 bp).
        | * `median5PrimeTo3PrimeBias`: Median value of 5' to 3' coverage biases.
        | * `medianInsertSize`: Median insert size (only for paired-end libraries).
        | * `nBasesAligned`: Number of bases aligned.
        | * `nBasesCoding`: Number of bases aligned in the coding regions.
        | * `nBasesIntergenic`: Number of bases aligned in the intergenic regions.
        | * `nBasesIntron`: Number of bases aligned in the intronic regions.
        | * `nBasesRibosomal`: Number of bases aligned to ribosomal gene regions.
        | * `nBasesUtr`: Number of bases aligned in the UTR regions.
        | * `normalizedTranscriptCoverage`: Array representing normalized coverage along transcripts. The transcripts
        | come from the top 1000 expressed genes and each item in the array represents 1% of the transcript length.
        | * `nReadsAligned`: Number of reads aligned.
        | * `nReadsSingleton`: Number of paired-end reads aligned as singletons.
        | * `nReadsTotal`: Number of reads.
        | * `nReadsProperPair`: Number of paired-end reads aligned as proper pairs.
        | * `pctChimeras`: Percentage of reads aligned as chimeras (only for paired-end libraries).
        | * `rateIndel`: How much indels are present.
        | * `rateReadsMismatch`: Mismatch rate of aligned reads.
        | * `stdevInsertSize`: Insert size standard deviation (only for paired-end libraries).
      """.stripMargin
    parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("accLevel")
        .description(
          """The level at which the alignment statistics are gathered. Possible values are `lib` for library-level
            | accumulation or `sample` for sample-level accumulation (default: `sample`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedAccLevelParams.keySet.toList)
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included. This
            | parameter is considered to be unset if `accLevel` is set to `sample` regardless of user input since a
            | single sample may contain mixed library types.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[Boolean]("sorted")
        .description(
          """Whether to try sort the returned items or not. If set to false, the returned
            | results are randomized. If set to true, the returned items are guaranteed to be sorted. The sort order is
            | most often the items' creation time (most recent first), though this is not guaranteed (default: `false`).
          """.stripMargin.replaceAll("\n", ""))
        .optional,
      queryParam[String]("userId").description("User ID.")
        .optional,
      headerParam[String](HeaderApiKey).description("User API key.")
        .optional)
    responseMessages (
      StringResponseMessage(400, CommonMessages.InvalidAccLevel.message),
      StringResponseMessage(400, CommonMessages.InvalidLibType.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/gentrap/alignments", operation(statsGentrapAlignmentsGetOperation)) {

    logger.info(requestLog)
    val runSelector = ManyContainOne("runId", getRunObjectIds(params.getAs[String]("runIds")))
    val refSelector = ManyContainOne("referenceId", getRefObjectIds(params.getAs[String]("refIds")))
    val annotSelector = ManyIntersectMany("annotationIds", getAnnotObjectIds(params.getAs[String]("annotIds")))

    val sorted = params.getAs[Boolean]("sorted").getOrElse(false)

    val user = Try(simpleKeyAuth(params => params.get("userId"))).toOption
    if ((Option(request.getHeader(HeaderApiKey)).nonEmpty || params.get("userId").nonEmpty) && user.isEmpty)
      halt(401, CommonMessages.UnauthenticatedOptional)

    val accLevel = params.getAs[String]("accLevel")
      .collect {
        case p => p
          .asEnum(AllowedAccLevelParams)
          .getOrElse(halt(400, CommonMessages.InvalidAccLevel))
      }.getOrElse(AccLevel.Sample)

    val libSelector = {
      val lt = params.getAs[String]("libType")
        .collect {
          case p => p
            .asEnum(AllowedLibTypeParams)
            .getOrElse(halt(400, CommonMessages.InvalidLibType))
        }
      accLevel match {
        case AccLevel.Sample => EmptySelector
        case AccLevel.Lib    => Selector.fromLibType(lt)
      }
    }

    val matchers = Selector.combineAnd(runSelector, refSelector, annotSelector, libSelector)

    Ok(gentrap.getAlignmentStats(accLevel, matchers, user, sorted))
  }

  // format: OFF
  val statsGentrapAlignmentsAggregateGetOperation = (
    apiOperation[GentrapAlignmentStatsAggr]("statsGentrapAlignmentsAggregatesGet")
      summary "Retrieves the aggregate alignment statistics of Gentrap pipeline runs."
      notes
        """This endpoint returns aggregate values of various alignment-level metrics. The default settings are the same
          | as the corresponding data points endpoint. The aggregated metrics are also similar to the data points'
          | metrics, with the following additions:
          |
          | * `nBasesMrna`: Number of bases aligned in the UTR and coding region.
          | * `pctBasesCoding`: Percentage of bases aligned in the coding regions.
          | * `pctBasesIntergenic`: Percentage of bases aligned in the intergenic regions.
          | * `pctBasesIntron`: Percentage of bases aligned in the intronic regions.
          | * `pctBasesMrna`: Percentage of bases aligned in the UTR and coding region.
          | * `pctBasesRibosomal`: Percentage of bases aligned to ribosomal gene regions.
          | * `pctBasesUtr`: Percentage of bases aligned in the UTR regions.
          | * `pctReadsAlignedTotal`: Percentage of reads aligned (per total reads).
          | * `pctReadsAligned`: Percentage of reads aligned (per aligned reads).
          | * `pctReadsSingleton`: Percentage of paired-end reads aligned as singletons (per aligned reads).
          | * `pctReadsProperPair`: Percentage of paired-end reads aligned as proper pairs (per aligned reads).
          |
          |The following data point attribute is not aggregated:
          |
          | * `normalizedTranscriptCoverage`
          |
          |Each aggregated metric contains the attributes `avg` (average), `max` (maximum), `min` (minimum), `median`
          | (median), and `stdev` (standard deviation). It also contains the `nDataPoints` attribute, showing the number
          | of data points aggregated for the metrics.
        """.stripMargin
      parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("accLevel")
        .description(
          """The level at which the alignment statistics are gathered. Possible values are `lib` for library-level
            | accumulation or `sample` for sample-level accumulation (default: `sample`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedAccLevelParams.keySet.toList)
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included. This
            | parameter is considered to be unset if `accLevel` is set to `sample` regardless of user input since a
            | single sample may contain mixed library types.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional)
      responseMessages (
      StringResponseMessage(400, CommonMessages.InvalidAccLevel.message),
      StringResponseMessage(400, CommonMessages.InvalidLibType.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/gentrap/alignments/aggregate", operation(statsGentrapAlignmentsAggregateGetOperation)) {

    logger.info(requestLog)
    val runIds = getRunObjectIds(params.getAs[String]("runIds"))
    val refIds = getRefObjectIds(params.getAs[String]("refIds"))
    val annotIds = getAnnotObjectIds(params.getAs[String]("annotIds"))

    val accLevel = params.getAs[String]("accLevel")
      .collect {
        case p => p
          .asEnum(AllowedAccLevelParams)
          .getOrElse(halt(400, CommonMessages.InvalidAccLevel))
      }.getOrElse(AccLevel.Sample)

    val libType = {
      val lt = params.getAs[String]("libType")
        .collect {
          case p => p
            .asEnum(AllowedLibTypeParams)
            .getOrElse(halt(400, CommonMessages.InvalidLibType))
        }
      accLevel match {
        case AccLevel.Sample => None
        case AccLevel.Lib    => lt
      }
    }

    gentrap.getAlignmentAggr(accLevel, libType, runIds, refIds, annotIds) match {
      case None      => NotFound(CommonMessages.MissingDataPoints)
      case Some(res) => Ok(transformMapReduceResult(res))
    }
  }

  // format: OFF
  val statsGentrapSequencesGetOperation = (apiOperation[Seq[SeqStats]]("statsGentrapSequencesGet")
    summary "Retrieves the sequencing statistics of Gentrap pipeline runs."
    notes
      """This endpoint returns a list containing sequence-level metrics of Gentrap pipeline runs.
        |
        |By default:
        |
        | * Each data point represents an input set, which may consist of a single sequence (for single-end sequencing)
        |   or two sequences (for paired-end sequencing). Selection on library type (single, paired, or both) can be
        |   done via the `libType` parameter. When `libType` is set to `paired`, the data point will contain a `readAll`
        |   attribute denoting the combined metrics of both `read1` and `read2`.
        |
        | * The returned data points are computed from the raw sequence files. To return data points of the processed
        |   sequence files (possibly adapter-clipped and/or trimmed), use the `qcPhase` parameter.
        |
        | * Data points are returned in random order which changes in every query. To maintain a sorted order
        |   (most-recently created first), use the `sorted` parameter.
        |
        | * All data points of all Gentrap runs are returned. The filter for specific data points, use the `runIds`,
        |   `refIds`, and/or `annotIds` parameter.
        |
        | * All data points are unlabeled. To label the data points with their respective IDs and names, you must be
        |   the data points' uploader and authenticate yourself using your API key. If the returned data points contain
        |   data points you did not upload, they will remain unlabeled.
        |
        |Each `read*` attribute contains the following metrics:
        |
        | * `nBases`: Total number of bases across all reads.
        | * `nBasesA`: Total number of adenine bases across all reads.
        | * `nBasesT`: Total number of thymines across all reads.
        | * `nBasesG`: Total number of guanines across all reads.
        | * `nBasesC`: Total number of cytosines across all reads.
        | * `nBasesN`: Total number of unknown bases across all reads.
        | * `nReads`: Total number of reads.
        | * `nBasesByQual`: Array indicating how many bases have a given quality. The quality value corresponds to the
        |   array index (e.g. array(10) shows how many bases have quality value 10 as quality values start from 0).
        | * `medianQualByPosition`: Array indicating the median quality value for a given read position. The position
        |   correspond to the array index (e.g. array(20) shows the median quality value of read position 21 since
        |   position starts from 1).
      """.stripMargin
    parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[String]("qcPhase")
        .description(
          """Selects for the sequencing QC phase to return. Possible values are `raw` for raw data before any QC
            | is done and `processed` for sequencing statistics just before alignment (default: `raw`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedSeqQcPhaseParams.keySet.toList)
        .optional,
      queryParam[Boolean]("sorted")
        .description(
          """Whether to try sort the returned items or not. If set to false, the returned
            | results are randomized. If set to true, the returned items are guaranteed to be sorted. The sort order is
            | most often the items' creation time (most recent first), though this is not guaranteed (default: `false`).
          """.stripMargin.replaceAll("\n", ""))
        .optional,
      queryParam[String]("userId").description("User ID.")
        .optional,
      headerParam[String](HeaderApiKey).description("User API key.")
        .optional)
    responseMessages (
      StringResponseMessage(400, CommonMessages.InvalidLibType.message),
      StringResponseMessage(400, CommonMessages.InvalidSeqQcPhase.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/gentrap/sequences", operation(statsGentrapSequencesGetOperation)) {

    logger.info(requestLog)
    val runIds = getRunObjectIds(params.getAs[String]("runIds"))
    val refIds = getRefObjectIds(params.getAs[String]("refIds"))
    val annotIds = getAnnotObjectIds(params.getAs[String]("annotIds"))
    val sorted = params.getAs[Boolean]("sorted").getOrElse(false)

    val user = Try(simpleKeyAuth(params => params.get("userId"))).toOption
    if ((Option(request.getHeader(HeaderApiKey)).nonEmpty || params.get("userId").nonEmpty) && user.isEmpty)
      halt(401, CommonMessages.UnauthenticatedOptional)

    val qcPhase = params.getAs[String]("qcPhase")
      .collect {
        case p => p.asEnum(AllowedSeqQcPhaseParams)
          .getOrElse(halt(400, CommonMessages.InvalidSeqQcPhase))
      }.getOrElse(SeqQcPhase.Raw)

    val libType = params.getAs[String]("libType")
      .collect {
        case libn => libn.asEnum(AllowedLibTypeParams)
          .getOrElse(halt(400, CommonMessages.InvalidLibType))
      }

    val queryFunc = qcPhase match {
      case SeqQcPhase.Raw       => gentrap.getSeqStatsRaw
      case SeqQcPhase.Processed => gentrap.getSeqStatsProcessed
    }

    Ok(queryFunc(libType, user, runIds, refIds, annotIds, sorted))
  }

  // format: OFF
  val statsGentrapSequencesAggregateGetOperation =
    (apiOperation[SeqStatsAggr[ReadStatsAggr]]("statsGentrapSequencesAggregationsGet")
      summary "Retrieves the aggregate sequencing statistics of Gentrap pipeline runs."
      notes
        """This endpoint returns aggregate values of various sequence-level metrics. The default settings are the same
          | as the corresponding data points endpoint. The aggregated metrics are also similar to the data points'
          | metrics' with the following additions:
          |
          | * `pctBases`: Percentage of bases across all reads.
          | * `pctBasesA`: Percentage of adenine bases across all reads.
          | * `pctBasesT`: Percentage of thymines across all reads.
          | * `pctBasesG`: Percentage of guanines across all reads.
          | * `pctBasesC`: Percentage of cytosines across all reads.
          | * `pctBasesN`: Percentage of unknown bases across all reads.
          | * `pctBasesGC`: Percentage of guanine and cytosine bases across all reads.
          |
          |The following data point attributes not aggregated:
          |
          | * `nBasesByQual`
          | * `medianQualByPosition`
          |
          |Each aggregated metric contains the attributes `avg` (average), `max` (maximum), `min` (minimum), `median`
          | (median), and `stdev` (standard deviation). It also contains the `nDataPoints` attribute, showing the number
          | of data points aggregated for the metrics.
        """.stripMargin
      parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[String]("qcPhase")
        .description(
          """Selects for the sequencing QC phase to return. Possible values are `raw` for raw data before any QC
            | is done and `processed` for sequencing statistics just before alignment (default: `raw`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedSeqQcPhaseParams.keySet.toList)
        .optional)
      responseMessages (
      StringResponseMessage(400, CommonMessages.InvalidLibType.message),
      StringResponseMessage(400, CommonMessages.InvalidSeqQcPhase.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid."),
      StringResponseMessage(404, CommonMessages.MissingDataPoints.message)))
  // format: ON

  get("/gentrap/sequences/aggregate", operation(statsGentrapSequencesAggregateGetOperation)) {

    logger.info(requestLog)
    val runIds = getRunObjectIds(params.getAs[String]("runIds"))
    val refIds = getRefObjectIds(params.getAs[String]("refIds"))
    val annotIds = getAnnotObjectIds(params.getAs[String]("annotIds"))

    val qcPhase = params.getAs[String]("qcPhase")
      .collect {
        case p => p.asEnum(AllowedSeqQcPhaseParams)
          .getOrElse(halt(400, CommonMessages.InvalidSeqQcPhase))
      }.getOrElse(SeqQcPhase.Raw)

    val libType = params.getAs[String]("libType")
      .collect {
        case libn => libn.asEnum(AllowedLibTypeParams)
          .getOrElse(halt(400, CommonMessages.InvalidLibType))
      }

    val queryFunc = qcPhase match {
      case SeqQcPhase.Raw       => gentrap.getSeqStatsAggrRaw
      case SeqQcPhase.Processed => gentrap.getSeqStatsAggrProcessed
    }

    queryFunc(libType, runIds, refIds, annotIds) match {
      case None      => NotFound(CommonMessages.MissingDataPoints)
      case Some(res) => Ok(transformMapReduceResult(res))
    }
  }
}
