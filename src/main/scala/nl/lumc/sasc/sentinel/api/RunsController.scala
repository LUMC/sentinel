package nl.lumc.sasc.sentinel.api

import java.io.File
import scala.util.{ Failure, Success, Try }

import org.json4s.jackson.Serialization
import org.scalatra._
import org.scalatra.swagger._
import org.scalatra.servlet.{ FileItem, FileUploadSupport, MultipartConfig, SizeConstraintExceededException }

import nl.lumc.sasc.sentinel.{ AllowedPipelineParams, MaxRunSummarySize, MaxRunSummarySizeMb, Pipeline }
import nl.lumc.sasc.sentinel.api.auth.AuthenticationSupport
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04InputProcessor
import nl.lumc.sasc.sentinel.processors.unsupported.UnsupportedInputProcessor
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils._

class RunsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with FileUploadSupport
    with AuthenticationSupport { self =>

  protected val applicationDescription: String = "Submission and retrieval of run summaries"
  override protected val applicationName = Some("runs")

  val runs = new RunsAdapter with MongodbConnector {
    val mongo = self.mongo
    def processRun(fi: FileItem, userId: String, pipeline: String) = Try(throw new NotImplementedError)
  }
  val users = new UsersAdapter with MongodbConnector { val mongo = self.mongo }
  val unsupported = new UnsupportedInputProcessor(mongo)
  val gentrap = new GentrapV04InputProcessor(mongo)

  protected def maxFileSize = MaxRunSummarySize

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(maxFileSize)))

  error {
    case e: SizeConstraintExceededException =>
      contentType = formats("json")
      RequestEntityTooLarge(
        Serialization.write(
          ApiMessage(s"Run summary exceeds $MaxRunSummarySizeMb MB."))
      )
  }

  // format: OFF
  val runsRunIdDeleteOperation = (apiOperation[Unit]("runsRunIdDelete")
    summary "Deletes an uploaded run summary."
    notes
      """This endpoint deletes an uploaded run summary. Only administrators and the run summary uploader can delete a
        | run summary.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("runId").description("Run summary ID."),
      queryParam[String]("userId").description("Run summary uploader ID."))
    responseMessages (
      StringResponseMessage(204, "Run summary deleted successfully."),
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found.")))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  delete("/:runId", operation(runsRunIdDeleteOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthenticated
    // TODO: return 403 if unauthorized
    // TODO: return 204 if delete successful
  }

  // format: OFF
  val runsRunIdGetOperation = (apiOperation[File]("runsRunIdGet")
    summary "Retrieves single run summaries."
    notes
      """This endpoint retrieves the a single record of an uploaded summary. Optionally, you can also download the
        | actual summary file by specifying the `download` parameter. Only administrators and the run summary uploader
        | can access this resource.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("runId").description("Run summary ID."),
      queryParam[String]("userId").description("Run summary uploader ID."),
      queryParam[Boolean]("download").description("Whether to download the raw summary file or not.").optional)
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingRunId.message),
      StringResponseMessage(410, "Run summary not available anymore."))
    // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
    produces (
      "application/json",
      "application/octet-stream"))
  // format: ON

  get("/:runId", operation(runsRunIdGetOperation)) {
    // Since there is no standard to define boolean in query parameter, we try to capture the common ones
    val doDownload = params.get("download") match {
      case None => false
      case Some(p) => p.toLowerCase match {
        case "0" | "no" | "false" | "null" | "none" | "nothing" => false
        case otherwise => true
      }
    }
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 410 if run was available but has been deleted
    val user = simpleKeyAuth(params => params.get("userId"))
    runs.getRun(runId, user.id, doDownload) match {
      case None => NotFound(CommonErrors.MissingRunId)
      case Some(result) => result match {
        case Left(runDoc) => Ok(runDoc)
        case Right(runFile) =>
          contentType = "application/octet-stream"
          response.setHeader("Content-Disposition",
            "attachment; filename=" + runFile.filename.getOrElse(s"$runId.download"))
          Ok(runFile.inputStream)
      }
    }
  }

  // format: OFF
  val runsPostOperation = (apiOperation[RunDocument]("runsPost")
    summary "Uploads a JSON run summary."
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      queryParam[String]("pipeline")
        .description("Name of the pipeline that produces the uploaded summary. Valid values are `gentrap` or `unsupported`.")
        .allowableValues(AllowedPipelineParams.keySet.toList),
      formParam[File]("run").description("Run summary file."))
    responseMessages (
      StringResponseMessage(201, "Run summary added."),
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(400, CommonErrors.UnspecifiedPipeline.message),
      StringResponseMessage(400, CommonErrors.InvalidPipeline.message),
      StringResponseMessage(400, "Run summary is unspecified or invalid."),
      StringResponseMessage(400, "Run summary already uploaded by the user."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(413, "Run summary too large.")))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  post("/", operation(runsPostOperation)) {
    val pipeline = params.getOrElse("pipeline", halt(400, CommonErrors.UnspecifiedPipeline))
    val uploadedRun = fileParams.getOrElse("run", halt(400, ApiMessage("Run summary file not specified.")))

    val processor = AllowedPipelineParams.get(pipeline).collect {
      case Pipeline.Gentrap     => gentrap
      case Pipeline.Unsupported => unsupported
    }

    processor match {
      case None => BadRequest(CommonErrors.InvalidPipeline)
      case Some(p) =>
        val user = simpleKeyAuth(params => params.get("userId"))
        p.processRun(uploadedRun, user.id, pipeline) match {
          case Failure(f) =>
            f match {
              case vexc: RunValidationException =>
                BadRequest(ApiMessage(vexc.getMessage, data = vexc.validationErrors.map(_.getMessage)))
              case dexc: com.mongodb.DuplicateKeyException =>
                BadRequest(ApiMessage("Run summary already uploaded by the user."))
              case otherwise =>
                InternalServerError(CommonErrors.Unexpected)
            }
          case Success(run) => Created(run)
        }
    }
  }

  // format: OFF
  val runsGetOperation = (apiOperation[List[RunDocument]]("runsGet")
    summary "Retrieves run summary records."
    notes
      """This endpoint retrieves run summaries uploaded by the given user sorted by last upload date first.
        | Only administrators and the run summary uploader can access this resource. Note that this endpoint omits the
        | actual run summary content. To retrieve the run summary content, you must specify its ID using another
        | endpoint.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      queryParam[List[String]]("pipelines")
        .description(
          """Filters for summaries produced by the given pipeline. Valid values are `gentrap`, `unsupported`. If not
            | specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedPipelineParams.keySet.toList)
        .optional)
      responseMessages (
        StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
        StringResponseMessage(400, "One or more pipeline is invalid."),
        StringResponseMessage(401, CommonErrors.Unauthenticated.message),
        StringResponseMessage(403, CommonErrors.Unauthorized.message)))
  // TODO: add authorizations entry *after* scalatra-swagger fixes the spec deviation
  // format: ON

  get("/", operation(runsGetOperation)) {
    val pipelines = splitParam(params.getAs[String]("pipelines"))
    val (validPipelines, invalidPipelines) = pipelines.partition { AllowedPipelineParams.contains }

    if (invalidPipelines.nonEmpty)
      halt(400, ApiMessage("One or more pipeline is invalid.", data = Map("invalid pipelines" -> invalidPipelines)))
    else {
      val user = simpleKeyAuth(params => params.get("userId"))
      runs.getRuns(user.id, validPipelines)
    }
  }
}
