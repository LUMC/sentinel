package nl.lumc.sasc.sentinel.api

import java.io.File

import org.json4s.jackson.Serialization
import org.json4s.mongo.ObjectIdSerializer
import org.scalatra._
import org.scalatra.swagger._
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{ FileUploadSupport, MultipartConfig, SizeConstraintExceededException }

import nl.lumc.sasc.sentinel.{ AllowedPipelineParams, Pipeline }
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.processors.gentrap.GentrapV04InputProcessor
import nl.lumc.sasc.sentinel.processors.unsupported.UnsupportedInputProcessor
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils._

class RunsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends ScalatraServlet
  with JacksonJsonSupport
  with FileUploadSupport
  with SwaggerSupport {

  protected val applicationDescription: String = "Submission and retrieval of run summaries"
  override protected val applicationName = Some("runs")

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats + new ObjectIdSerializer

  val gentrap = new GentrapV04InputProcessor(mongo)
  val unsupported = new UnsupportedInputProcessor(mongo)

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  protected def maxFileSize = 16 * 1024 * 1024

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(maxFileSize)))

  error {
    case e: SizeConstraintExceededException =>
      contentType = formats("json")
      RequestEntityTooLarge(
        Serialization.write(
          ApiError("Run summary exceeds " + (maxFileSize / (1024 * 1024)).toString + " MB."))
      )
  }

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
      StringResponseMessage(404, "User ID or run summary ID not found."))
  )

  delete("/:runId", operation(runsRunIdDeleteOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthenticated
    // TODO: return 403 if unauthorized
    // TODO: return 204 if delete successful
  }

  val runsRunIdGetOperation = (apiOperation[File]("runsRunIdGet")
    summary "Retrieves full run summaries."
    notes
      """This endpoint retrieves single run summaries. Only administrators and the run summary uploader can access this
        | resource.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("runId").description("Run summary ID."),
      queryParam[String]("userId").description("Run summary uploader ID."))
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthenticated.message),
      StringResponseMessage(403, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found."),
      StringResponseMessage(410, "Run summary not available anymore."))
    produces "application/octet-stream"
  )

  get("/:runId", operation(runsRunIdGetOperation)) {
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthenticated
    // TODO: return 403 if unauthorized
    // TODO: return 410 if run was available but has been deleted
    // TODO: return 200 and run summary
    contentType = "application/octet-stream"
  }

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
      StringResponseMessage(404, CommonErrors.MissingUserId.message),
      StringResponseMessage(413, "Run summary too large."))
  )

  post("/", operation(runsPostOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val pipeline = params.getOrElse("pipeline", halt(400, CommonErrors.UnspecifiedPipeline))
    val uploadedRun = fileParams.getOrElse("run", halt(400, "Run summary file not specified."))
    // TODO: return 413 if file is too large
    // TODO: return 404 if user not found
    // TODO: return 401 if not authenticated
    // TODO: return 403 if not authorized
    // TODO: return 400 if any other error occurs (???)

    val processor = AllowedPipelineParams.get(pipeline).collect {
      case Pipeline.Gentrap     => gentrap
      case Pipeline.Unsupported => unsupported
    }

    processor match {
      case None     => BadRequest(CommonErrors.InvalidPipeline)
      case Some(p)  =>
        p.processRun(uploadedRun, userId, pipeline) match {
          case scala.util.Failure(f)    =>
            log(f.getMessage, f)
            f match {
              case vexc: RunValidationException =>
                BadRequest(ApiError(vexc.getMessage, data = vexc.validationErrors.map(_.getMessage)))
              case dexc: DuplicateRunException =>
                BadRequest(ApiError("Run summary already uploaded by the user."))
              case otherwise =>
                InternalServerError(CommonErrors.Unexpected)
            }
          case scala.util.Success(run)  => Created(run)
        }
    }
  }

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
        StringResponseMessage(401, CommonErrors.Unauthenticated.message),
        StringResponseMessage(403, CommonErrors.Unauthorized.message),
        StringResponseMessage(404, "One or more pipeline is invalid."),
        StringResponseMessage(404, CommonErrors.MissingUserId.message))
  )

  get("/", operation(runsGetOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val pipelines = splitParam(params.getAs[String]("pipelines"))
    // TODO: return 404 if user ID not found
    // TODO: return 401 if not authenticated
    // TODO: return 403 if unauthorized
    // TODO: return 200 and user's run summary
  }

}
