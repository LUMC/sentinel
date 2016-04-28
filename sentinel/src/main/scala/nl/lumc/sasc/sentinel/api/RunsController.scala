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
import nl.lumc.sasc.sentinel.processors.{ CompositeRunsProcessor, RunsProcessor }
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
class RunsController[T <: RunsProcessor](implicit val swagger: Swagger, mongo: MongodbAccessObject,
                                         runsProcessors: Seq[MongodbAccessObject => T])
    extends SentinelServlet
    with FileUploadSupport
    with AuthenticationSupport { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName = Some("runs")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Submission and retrieval of run summaries"

  /** Adapter for connecting to users collection. */
  val users = new UsersAdapter { val mongo = self.mongo }

  /** Processor for connecting to different run processors. */
  val runs = new CompositeRunsProcessor(runsProcessors.map { rp => rp(mongo) })

  /** Container for supported pipelines. */
  protected lazy val supportedPipelines = runs.processorsMap

  /** Documentation string for available pipeline parameters. */
  protected lazy val supportedPipelineParams = "`" + supportedPipelines.keySet.mkString("`, `") + "`"

  /** Set maximum allowed file upload size. */
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxRunSummarySize)))

  /** General error handler for any type of exception. */
  error {
    case sexc: SizeConstraintExceededException =>
      contentType = formats("json")
      RequestEntityTooLarge(Payloads.RunSummaryTooLargeError)

    case otherwise =>
      contentType = formats("json")
      InternalServerError(Payloads.UnexpectedError)
  }

  options("/?") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,POST")
  }

  options("/:runId") {
    logger.info(requestLog)
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    response.setHeader("Access-Control-Allow-Methods", "DELETE,GET,HEAD,PATCH")
    response.setHeader("Accept-Patch", formats("json"))
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
      StringResponseMessage(400, Payloads.UnspecifiedRunIdError.message),
      StringResponseMessage(401, Payloads.AuthenticationError.message),
      StringResponseMessage(403, Payloads.AuthorizationError.message),
      StringResponseMessage(404, Payloads.RunIdNotFoundError.message),
      StringResponseMessage(410, Payloads.ResourceGoneError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  delete("/:runId", operation(runIdDeleteOp)) {
    logger.info(requestLog)
    val runId = params.getAs[DbId]("runId").getOrElse(halt(404, Payloads.RunIdNotFoundError))
    val user = simpleKeyAuth(params => params.get("userId"))

    new AsyncResult {
      val is = runs.deleteRun(runId, user).map {
        case \/-(doc) => Ok(doc)
        case -\/(err) => err.actionResult
      }
    }
  }

  // Helper matcher for "DELETE /:runId" so that we return the correct error message
  delete("/?") { halt(400, Payloads.UnspecifiedRunIdError) }

  // format: OFF
  val runIdPatchOp = (apiOperation[Unit]("runIdPatch")
    summary "Updates an uploaded run record."
    notes
    """This endpoint is used for updating an existing run record. Operations are defined using a subset of the JSON
      | patch specification. Only the `add`, `remove`, and `replace` operations on tags are supported and only
      | administrators or the run record uploader can perform patching.
    """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("User ID."),
      pathParam[String]("runId").description("Run ID to patch."),
      headerParam[String](HeaderApiKey).description("User API key."),
      bodyParam[SinglePathPatch]("body").description("Patch operations to apply."))
    responseMessages (
    StringResponseMessage(204, "Record(s) patched successfully."),
    StringResponseMessage(400, "Patch document is invalid or malformed."),
    StringResponseMessage(400, Payloads.UnspecifiedRunIdError.message),
    StringResponseMessage(401, Payloads.AuthenticationError.message),
    StringResponseMessage(403, Payloads.AuthorizationError.message),
    StringResponseMessage(404, Payloads.RunIdNotFoundError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  patch("/:runId", operation(runIdPatchOp)) {

    logger.info(requestLog)

    val runId = params.getAs[DbId]("runId").getOrElse(halt(404, Payloads.RunIdNotFoundError))
    val user = simpleKeyAuth(params => params.get("userId"))

    new AsyncResult {
      val is =
        runs.patchAndUpdateRun(runId, user, request.body.getBytes).map {
          case -\/(err) => err.actionResult
          case \/-(_)   => NoContent()
        }
    }
  }

  // Helper endpoint to capture PATCH request with unspecified user ID
  patch("/?") { halt(400, Payloads.UnspecifiedRunIdError) }

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
      queryParam[Boolean]("showUnitsLabels")
        .description(
          """Whether to show the samples and/or read groups belonging to the queried run or not (default: `false`). This
            |parameter has no effect when the `download` parameter is set to `true`.
          """.stripMargin)
        .defaultValue(false),
      queryParam[Boolean]("download").description("Whether to download the raw summary file or not.").optional,
      queryParam[Boolean]("showBlanks")
        .description("Whether to display JSON with `null` attributes and empty containers or remove them completely.")
        .defaultValue(false)
        .optional)
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, Payloads.AuthenticationError.message),
      StringResponseMessage(403, Payloads.AuthorizationError.message),
      StringResponseMessage(404, Payloads.RunIdNotFoundError.message),
      StringResponseMessage(410, "Run summary not available anymore."))
    // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
    produces (
      "application/json",
      "application/octet-stream"))
  // format: ON

  get("/:runId", operation(runIdGetOp)) {
    logger.info(requestLog)
    val doDownload = paramsGetter.downloadRunSummary(params)
    val showUnitsLabels = paramsGetter.showUnitLabels(params)
    val runId = params.getAs[DbId]("runId").getOrElse(halt(404, Payloads.RunIdNotFoundError))
    val user = simpleKeyAuth(params => params.get("userId"))

    new AsyncResult {
      val is =
        if (doDownload) runs.getRunFile(runId, user).map {
          case None => NotFound(Payloads.RunIdNotFoundError)
          case Some(runFile) =>
            contentType = "application/octet-stream"
            response.setHeader("Content-Disposition",
              "attachment; filename=" + runFile.filename.getOrElse(s"$runId.download"))
            Ok(runFile.inputStream)
        }
        else runs.getRun(runId, user, showUnitsLabels).map {
          case -\/(err) => err.actionResult
          case \/-(res) => Ok(res)
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
      StringResponseMessage(400, Payloads.UnspecifiedUserIdError.message),
      StringResponseMessage(400, Payloads.UnspecifiedPipelineError.message),
      StringResponseMessage(400, "Pipeline parameter is invalid."),
      StringResponseMessage(400, "Run summary is unspecified or invalid."),
      StringResponseMessage(401, Payloads.AuthenticationError.message),
      StringResponseMessage(403, Payloads.AuthorizationError.message),
      StringResponseMessage(409, "Run summary already uploaded by the user."),
      StringResponseMessage(413, Payloads.RunSummaryTooLargeError.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  post("/", operation(postOp)) {
    logger.info(requestLog)
    val pipeline = params.getOrElse("pipeline", halt(400, Payloads.UnspecifiedPipelineError))
    val upload = fileParams.getOrElse("run", halt(400, ApiPayload("Run summary file not specified.")))

    supportedPipelines.get(pipeline) match {

      case None => BadRequest(Payloads.InvalidPipelineError(supportedPipelines.keySet.toSeq))

      case Some(_) =>
        val user = simpleKeyAuth(params => params.get("userId"))
        new AsyncResult {
          val is = {
            runs.processRunUpload(pipeline, upload.readUncompressedBytes(), upload.getName, user)
              .map {
                case -\/(err) => err.actionResult
                case \/-(run) => Created(run)
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
        StringResponseMessage(400, Payloads.UnspecifiedUserIdError.message),
        StringResponseMessage(400, "One or more pipeline is invalid."),
        StringResponseMessage(401, Payloads.AuthenticationError.message),
        StringResponseMessage(403, Payloads.AuthorizationError.message)))
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
          runs
            .getRuns(user, validPipelines)
            .map {
              case -\/(err) => err.actionResult
              case \/-(res) => Ok(res)
            }
      }
    }
  }
}
