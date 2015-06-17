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
import org.scalatra.servlet.FileItem
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel._
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
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
  protected val runs = new RunsAdapter {
    val mongo = self.mongo
    def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) = Try(throw new NotImplementedError)
  }

  /** Adapter for connecting to the gentrap collection */
  protected val gentrap = new GentrapOutputProcessor(mongo)

  /** Adapter for connecting to the users collection */
  protected val users = new UsersAdapter { val mongo = self.mongo }

  // format: OFF
  val statsRunsGetOperation = (apiOperation[Seq[PipelineStats]]("statsRunsGet")
    summary "Retrieves general statistics of uploaded run summaries.")
  // format: ON

  get("/runs", operation(statsRunsGetOperation)) {
    Ok(runs.getGlobalRunStats())
  }

  // format: OFF
  val statsGentrapAlignmentsGetOperation = (apiOperation[Seq[GentrapAlignmentStats]]("statsGentrapAlignmentsGet")
    summary "Retrieves the alignment statistics of Gentrap pipeline runs."
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

    val runIds = getRunObjectIds(params.getAs[String]("runIds"))
    val refIds = getRefObjectIds(params.getAs[String]("refIds"))
    val annotIds = getAnnotObjectIds(params.getAs[String]("annotIds"))
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

    Ok(gentrap.getAlignmentStats(accLevel, libType, user, runIds, refIds, annotIds, sorted))
  }

  // format: OFF
  val statsGentrapAlignmentsAggregateGetOperation = (
    apiOperation[GentrapAlignmentStatsAggr]("statsGentrapAlignmentsAggregatesGet")
      summary "Retrieves the aggregate alignment statistics of Gentrap pipeline runs."
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

    gentrap.getAlignmentAggregateStats(accLevel, libType, runIds, refIds, annotIds) match {
      case None      => NotFound(CommonMessages.MissingDataPoints)
      case Some(res) => Ok(transformMapReduceResult(res))
    }
  }

  // format: OFF
  val statsGentrapSequencesGetOperation = (apiOperation[Seq[SeqStats]]("statsGentrapSequencesGet")
    summary "Retrieves the sequencing statistics of Gentrap pipeline runs."
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

    Ok(gentrap.getSeqStats(libType, qcPhase, user, runIds, refIds, annotIds, sorted))
  }

  // format: OFF
  val statsGentrapSequencesAggregateGetOperation =
    (apiOperation[SeqStatsAggr]("statsGentrapSequencesAggregationsGet")
      summary "Retrieves the aggregate sequencing statistics of Gentrap pipeline runs."
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

    gentrap.getSeqAggregateStats(libType, qcPhase, runIds, refIds, annotIds) match {
      case None      => NotFound(CommonMessages.MissingDataPoints)
      case Some(res) => Ok(transformMapReduceResult(res))
    }
  }
}
