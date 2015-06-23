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
import scala.util.{ Failure, Success, Try }

import org.scalatra._
import org.scalatra.swagger._
import org.scalatra.servlet.{ FileItem, FileUploadSupport, MultipartConfig, SizeConstraintExceededException }

import nl.lumc.sasc.sentinel.{ AllowedPipelineParams, HeaderApiKey, Pipeline }
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04InputProcessor
import nl.lumc.sasc.sentinel.processors.unsupported.UnsupportedInputProcessor
import nl.lumc.sasc.sentinel.settings._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils._
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Controller for the `/runs` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class RunsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with FileUploadSupport
    with AuthenticationSupport { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName = Some("runs")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Submission and retrieval of run summaries"

  /** Adapter for connecting to run collections. */
  val runs = new RunsAdapter {
    val mongo = self.mongo
    def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) = Try(throw new NotImplementedError)
  }

  /** Adapter for connecting to users collection. */
  val users = new UsersAdapter { val mongo = self.mongo }

  /** Adapter for connecting to the unsupported summary collections. */
  val unsupported = new UnsupportedInputProcessor(mongo)

  /** Adapter for connecting to the gentrap summary collections. */
  val gentrap = new GentrapV04InputProcessor(mongo)

  /** Set maximum allowed file upload size. */
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxRunSummarySize)))

  /** Add error handler for files that exceed maximum allowed size. */
  error {
    case e: SizeConstraintExceededException =>
      contentType = formats("json")
      RequestEntityTooLarge(CommonMessages.RunSummaryTooLarge)
  }

  // format: OFF
  val runsRunIdDeleteOperation = (apiOperation[Unit]("runsRunIdDelete")
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
      StringResponseMessage(410, "Run summary already deleted.")))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  delete("/:runId", operation(runsRunIdDeleteOperation)) {
    val runId = params("runId")
      .getObjectId
      .getOrElse(halt(404, CommonMessages.MissingRunId))
    val user = simpleKeyAuth(params => params.get("userId"))
    runs.deleteRun(runId, user) match {
      case None => NotFound(CommonMessages.MissingRunId)
      case Some((deletedRun, deletionPerformed)) =>
        if (deletionPerformed) Ok(deletedRun)
        else Gone(ApiMessage("Run summary already deleted."))
    }
  }

  // Helper matcher for "DELETE /:runId" so that we return the correct error message
  delete("/?") { halt(400, CommonMessages.UnspecifiedRunId) }

  // format: OFF
  val runsRunIdGetOperation = (apiOperation[File]("runsRunIdGet")
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

  get("/:runId", operation(runsRunIdGetOperation)) {
    val doDownload = params.getAs[Boolean]("download").getOrElse(false)
    val runId = params("runId")
      .getObjectId
      .getOrElse(halt(404, CommonMessages.MissingRunId))
    val user = simpleKeyAuth(params => params.get("userId"))

    if (doDownload) runs.getRunFile(runId, user) match {
      case None => NotFound(CommonMessages.MissingRunId)
      case Some(runFile) =>
        contentType = "application/octet-stream"
        response.setHeader("Content-Disposition",
          "attachment; filename=" + runFile.filename.getOrElse(s"$runId.download"))
        Ok(runFile.inputStream)
    }

    else runs.getRunRecord(runId, user) match {
      case None         => NotFound(CommonMessages.MissingRunId)
      case Some(runDoc) => Ok(runDoc)
    }
  }

  // format: OFF
  val runsPostOperation = (apiOperation[RunRecord]("runsPost")
    summary "Uploads a JSON run summary."
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      headerParam[String](HeaderApiKey).description("User API key."),
      queryParam[String]("pipeline")
        .description("Name of the pipeline that produces the uploaded summary. Valid values are `gentrap` or `unsupported`.")
        .allowableValues(AllowedPipelineParams.keySet.toList),
      formParam[File]("run").description("Run summary file."))
    responseMessages (
      StringResponseMessage(201, "Run summary added."),
      StringResponseMessage(400, CommonMessages.UnspecifiedUserId.message),
      StringResponseMessage(400, CommonMessages.UnspecifiedPipeline.message),
      StringResponseMessage(400, CommonMessages.InvalidPipeline.message),
      StringResponseMessage(400, "Run summary is unspecified or invalid."),
      StringResponseMessage(401, CommonMessages.Unauthenticated.message),
      StringResponseMessage(403, CommonMessages.Unauthorized.message),
      StringResponseMessage(409, "Run summary already uploaded by the user."),
      StringResponseMessage(413, CommonMessages.RunSummaryTooLarge.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  post("/", operation(runsPostOperation)) {
    val pipeline = params.getOrElse("pipeline", halt(400, CommonMessages.UnspecifiedPipeline))
    val uploadedRun = fileParams.getOrElse("run", halt(400, ApiMessage("Run summary file not specified.")))

    val processor = AllowedPipelineParams.get(pipeline).collect {
      case Pipeline.Gentrap     => gentrap
      case Pipeline.Unsupported => unsupported
    }

    processor match {
      case None => BadRequest(CommonMessages.InvalidPipeline)
      case Some(p) =>
        val user = simpleKeyAuth(params => params.get("userId"))
        p.processRun(uploadedRun, user, AllowedPipelineParams(pipeline)) match {
          case Failure(f) =>
            f match {
              case vexc: RunValidationException =>
                BadRequest(ApiMessage(vexc.getMessage, data = vexc.report.collect { case r => r.toString }))
              case dexc: DuplicateFileException =>
                Conflict(ApiMessage(dexc.getMessage, data = Map("uploadedId" -> dexc.existingId)))
              case otherwise =>
                InternalServerError(CommonMessages.Unexpected)
            }
          case Success(run) => Created(run)
        }
    }
  }

  // format: OFF
  val runsGetOperation = (apiOperation[Seq[RunRecord]]("runsGet")
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
          """Filters for summaries produced by the given pipeline. Valid values are `gentrap`, `unsupported`. If not
            | specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedPipelineParams.keySet.toList)
        .optional)
      responseMessages (
        StringResponseMessage(400, CommonMessages.UnspecifiedUserId.message),
        StringResponseMessage(400, "One or more pipeline is invalid."),
        StringResponseMessage(401, CommonMessages.Unauthenticated.message),
        StringResponseMessage(403, CommonMessages.Unauthorized.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/", operation(runsGetOperation)) {
    val pipelines = splitParam(params.getAs[String]("pipelines"))
    val (validPipelines, invalidPipelines) = pipelines.partition { AllowedPipelineParams.contains }

    if (invalidPipelines.nonEmpty)
      halt(400, ApiMessage("One or more pipeline is invalid.", data = Map("invalid pipelines" -> invalidPipelines)))
    else {
      val user = simpleKeyAuth(params => params.get("userId"))
      runs.getRuns(user, validPipelines.map { AllowedPipelineParams.apply })
    }
  }
}
