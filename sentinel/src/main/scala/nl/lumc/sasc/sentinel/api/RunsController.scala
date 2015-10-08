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

import java.io.File

import org.scalatra._
import org.scalatra.swagger._
import org.scalatra.servlet.{ FileUploadSupport, MultipartConfig, SizeConstraintExceededException }
import scalaz._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.adapters._
import nl.lumc.sasc.sentinel.exts.plain._
import nl.lumc.sasc.sentinel.processors.RunsProcessor
import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.MongodbAccessObject
import nl.lumc.sasc.sentinel.utils.Implicits._

/**
 * Controller for the `/runs` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class RunsController(implicit val swagger: Swagger, mongo: MongodbAccessObject,
                     runsProcessors: Set[MongodbAccessObject => RunsProcessor] = Set.empty)
    extends SentinelServlet
    with FileUploadSupport
    with AuthenticationSupport { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName = Some("runs")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Submission and retrieval of run summaries"

  /** Adapter for connecting to run collections. */
  val runs = new PlainRunsProcessor(mongo)

  /** Adapter for connecting to users collection. */
  val users = new UsersAdapter { val mongo = self.mongo }

  /** Container for supported pipelines. */
  protected lazy val supportedPipelines = runsProcessors.map { f =>
    val proc = f(mongo)
    (proc.pipelineName, proc)
  }.toMap

  /** Documentation string for available pipeline parameters. */
  protected lazy val supportedPipelineParams = "`" + supportedPipelines.keySet.mkString("`, `") + "`"

  /** Set maximum allowed file upload size. */
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxRunSummarySize)))

  /** General error handler for any type of exception. */
  error {
    case sexc: SizeConstraintExceededException =>
      contentType = formats("json")
      RequestEntityTooLarge(CommonMessages.RunSummaryTooLarge)

    case otherwise =>
      contentType = formats("json")
      InternalServerError(CommonMessages.Unexpected)
  }

  options("/?") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,POST")
  }

  options("/:runId") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "DELETE,GET,HEAD")
  }

  // format: OFF
  val runIdDeleteOp = (apiOperation[Unit]("runIdDelete")
    summary "Deletes an uploaded run summary."
    notes
      """This endpoint deletes an uploaded run summary. Only administrators and the run summary uploader can delete a
        | run summary.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      headerParam[String](HeaderApiKey).description("Run summary uploader API key."),
      pathParam[String]("runId").description("Run summary ID."))
    responseMessages (
      StringResponseMessage(200, "Run summary deleted successfully."),
      StringResponseMessage(400, CommonMessages.UnspecifiedRunId.message),
      StringResponseMessage(401, CommonMessages.Unauthenticated.message),
      StringResponseMessage(403, CommonMessages.Unauthorized.message),
      StringResponseMessage(404, CommonMessages.MissingRunId.message),
      StringResponseMessage(410, CommonMessages.ResourceGoneError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  delete("/:runId", operation(runIdDeleteOp)) {
    logger.info(requestLog)
    val runId = params.getAs[DbId]("runId").getOrElse(halt(404, CommonMessages.MissingRunId))
    val user = simpleKeyAuth(params => params.get("userId"))

    new AsyncResult {
      val is = runs.deleteRun(runId, user).map {
        case \/-(doc) => Ok(doc)
        case -\/(err) => err match {
          case CommonMessages.IncompleteDeletionError => InternalServerError(err)
          case CommonMessages.ResourceGoneError       => Gone(err)
          case CommonMessages.MissingRunId            => NotFound(err)
          case otherwise                              => InternalServerError(CommonMessages.Unexpected)
        }
      }
    }

  }

  // Helper matcher for "DELETE /:runId" so that we return the correct error message
  delete("/?") { halt(400, CommonMessages.UnspecifiedRunId) }

  // format: OFF
  val runIdGetOp = (apiOperation[PlainRunRecord]("runIdGet")
    summary "Retrieves single run summaries."
    notes
      """This endpoint retrieves the a single record of an uploaded summary. Optionally, you can also download the
        | actual summary file by specifying the `download` parameter. Only administrators and the run summary uploader
        | can access this resource.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      headerParam[String](HeaderApiKey).description("Run summary uploader API key."),
      pathParam[String]("runId").description("Run summary ID."),
      queryParam[Boolean]("download").description("Whether to download the raw summary file or not.").optional)
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonMessages.Unauthenticated.message),
      StringResponseMessage(403, CommonMessages.Unauthorized.message),
      StringResponseMessage(404, CommonMessages.MissingRunId.message),
      StringResponseMessage(410, "Run summary not available anymore."))
    // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
    produces (
      "application/json",
      "application/octet-stream"))
  // format: ON

  get("/:runId", operation(runIdGetOp)) {
    logger.info(requestLog)
    val doDownload = params.getAs[Boolean]("download").getOrElse(false)
    val runId = params.getAs[DbId]("runId").getOrElse(halt(404, CommonMessages.MissingRunId))
    val user = simpleKeyAuth(params => params.get("userId"))

    new AsyncResult {
      val is =
        if (doDownload) runs.getRunFile(runId, user).map {
          case None => NotFound(CommonMessages.MissingRunId)
          case Some(runFile) =>
            contentType = "application/octet-stream"
            response.setHeader("Content-Disposition",
              "attachment; filename=" + runFile.filename.getOrElse(s"$runId.download"))
            Ok(runFile.inputStream)
        }
        else runs.getRunRecord(runId, user).map {
          case None         => NotFound(CommonMessages.MissingRunId)
          case Some(runDoc) => Ok(runDoc)
        }
    }
  }

  // format: OFF
  val postOp = (apiOperation[PlainRunRecord]("post")
    summary "Uploads a JSON run summary."
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      headerParam[String](HeaderApiKey).description("User API key."),
      queryParam[String]("pipeline")
        .description(s"Name of the pipeline that produces the uploaded summary. Valid values are $supportedPipelineParams.")
        .allowableValues(supportedPipelines.keys),
      formParam[File]("run").description("Run summary file."))
    responseMessages (
      StringResponseMessage(201, "Run summary added."),
      StringResponseMessage(400, CommonMessages.UnspecifiedUserId.message),
      StringResponseMessage(400, CommonMessages.UnspecifiedPipeline.message),
      StringResponseMessage(400, "Pipeline parameter is invalid."),
      StringResponseMessage(400, "Run summary is unspecified or invalid."),
      StringResponseMessage(401, CommonMessages.Unauthenticated.message),
      StringResponseMessage(403, CommonMessages.Unauthorized.message),
      StringResponseMessage(409, "Run summary already uploaded by the user."),
      StringResponseMessage(413, CommonMessages.RunSummaryTooLarge.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  post("/", operation(postOp)) {
    logger.info(requestLog)
    val pipeline = params.getOrElse("pipeline", halt(400, CommonMessages.UnspecifiedPipeline))
    val upload = fileParams.getOrElse("run", halt(400, ApiPayload("Run summary file not specified.")))

    supportedPipelines.get(pipeline) match {

      case None => BadRequest(CommonMessages.InvalidPipelineError(supportedPipelines.keySet.toSeq))

      case Some(p) =>
        val user = simpleKeyAuth(params => params.get("userId"))
        new AsyncResult {
          val is = {

            p.processRunUpload(upload.readUncompressedBytes(), upload.getName, user).map {

              case -\/(err) if err.message == CommonMessages.DuplicateSummaryError.message => Conflict(err)
              case -\/(err) if err.message == CommonMessages.JsonValidationError.message => BadRequest(err)
              case \/-(run) => Created(run)
              case otherwise => InternalServerError(CommonMessages.Unexpected)
            }
          }
        }
    }
  }

  // format: OFF
  val getOp = (apiOperation[Seq[PlainRunRecord]]("get")
    summary "Retrieves run summary records."
    notes
      """This endpoint retrieves run summaries uploaded by the given user sorted by last upload date first.
        | Only the run summary uploader can access this resource. Note that this endpoint omits the actual run summary
        | content. To retrieve the run summary content, you must specify its ID using another endpoint.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      headerParam[String](HeaderApiKey).description("Run summary uploader API key."),
      queryParam[Seq[String]]("pipelines")
        .description(
          s"""Filters for summaries produced by the given pipeline. Valid values are $supportedPipelineParams. If not
            | specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
        .allowableValues(supportedPipelines.keys)
        .optional)
      responseMessages (
        StringResponseMessage(400, CommonMessages.UnspecifiedUserId.message),
        StringResponseMessage(400, "One or more pipeline is invalid."),
        StringResponseMessage(401, CommonMessages.Unauthenticated.message),
        StringResponseMessage(403, CommonMessages.Unauthorized.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/", operation(getOp)) {

    logger.info(requestLog)

    val pipelines = params.getAs[Seq[String]]("pipelines").getOrElse(Seq.empty)
    val (validPipelines, invalidPipelines) = pipelines.partition { supportedPipelines.contains }

    if (invalidPipelines.nonEmpty)
      BadRequest(
        ApiPayload("One or more pipeline is invalid.",
          hints = List("invalid pipelines: " + invalidPipelines.mkString(", ") + ".")))
    else {
      val user = simpleKeyAuth(params => params.get("userId"))
      new AsyncResult {
        val is =
          runs.getRuns(user, validPipelines).map(res => Ok(res))
      }
    }
  }
}
