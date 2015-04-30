package nl.lumc.sasc.sentinel.api

import org.scalatra.ScalatraServlet
import org.scalatra.swagger._
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.FileUploadSupport

import nl.lumc.sasc.sentinel.AllowedPipelineParams
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.CommonErrors

class RunsController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with FileUploadSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "Submission and retrieval of run summaries"
  override protected val applicationName: Option[String] = Some("runs")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val runsRunIdDeleteOperation = (apiOperation[Unit]("runsRunIdDelete")
    summary "Deletes an uploaded run summary."
    notes
      """This endpoint deletes an uploaded run summary. Only administrators and the run
        |summary uploader can delete a run summary.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      pathParam[String]("runId").description("Run summary ID."))
    responseMessages (
      StringResponseMessage(204, "Run summary deleted successfully."),
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found."))
  )

  delete("/:runId", operation(runsRunIdDeleteOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthorized
    // TODO: return 204 if delete successful
  }

  val runsRunIdGetOperation = (apiOperation[RunSummary]("runsRunIdGet")
    summary "Retrieves a run summary with its full content."
    notes
      """This endpoint retrieves a single run summary. Only administrators and the run
        |summary uploader can access this resource.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      pathParam[String]("runId").description("Run summary ID."))
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found."),
      StringResponseMessage(410, "Run summary not available anymore."))
  )

  get("/:runId", operation(runsRunIdGetOperation)) {
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthorized
    // TODO: return 410 if run was available but has been deleted
    // TODO: return 200 and run summary
  }

  val runsPostOperation = (apiOperation[RunSummary]("runsPost")
    summary "Uploads a JSON run summary."
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      bodyParam[Any]("run-summary").description("Contents of the run summary."))
    responseMessages (
      StringResponseMessage(201, "Run summary added."),
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(400, "Run summary is invalid."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message))
  )

  post("/", operation(runsPostOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runSummary = parsedBody.extract[Any]
    // TODO: return 404 if user not found
    // TODO: return 401 if not authorized
    // TODO: return 400 if run summary is invalid
    // TODO: return 201 if post successful
  }

  val runsGetOperation = (apiOperation[List[RunSummary]]("runsGet")
    summary "Retrieves run summaries."
    notes
      """This endpoint retrieves run summaries uploaded by the given user sorted by last upload date first.
        |Only administrators and the run summary uploader can access this resource. Note that this endpoint omits the
        |actual run summary content. To retrieve the run summary content, you must specify its ID using another
        |endpoint.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      queryParam[String]("userId").description("Run summary uploader ID."),
      queryParam[String]("pipeline")
        .description(
          """Filters for summaries produced by the given pipeline. Valid values are `gentrap`, `unknown`. If not
            |specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedPipelineParams.toList)
        .optional)
      responseMessages (
        StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
        StringResponseMessage(401, CommonErrors.Unauthorized.message),
        StringResponseMessage(404, CommonErrors.MissingUserId.message))
  )

  get("/", operation(runsGetOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    // TODO: return 404 if user ID not found
    // TODO: return 401 if not authorized
    // TODO: return 200 and user's run summary
  }

}
