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

  val runsUserIdRunIdDeleteOperation = (apiOperation[Unit]("runsUserIdRunIdDelete")
    summary "Deletes an uploaded run summary."
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      pathParam[String]("runId").description("Run summary ID.")
    )
    responseMessages (
      StringResponseMessage(204, "Run summary deleted successfully."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found.")
    )
  )

  delete("/users/:userId/:runId", operation(runsUserIdRunIdDeleteOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthorized
    // TODO: return 204 if delete successful
  }

  val runsUserIdRunIdPatchOperation = (apiOperation[Unit]("runsUserIdRunIdPatch")
    summary "Updates an existing uploaded summary."
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      pathParam[String]("runId").description("Run summary ID."),
      bodyParam[RunSummaryPatch]("patch-operations").description("Patch operations to apply.")
    )
    responseMessages (
      StringResponseMessage(204, "Run summary patched successfully."),
      StringResponseMessage(400, "Invalid patch request."),
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found.")
    )
  )

  patch("/users/:userId/:runId", operation(runsUserIdRunIdPatchOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    val patchOperations = parsedBody.extract[RunSummaryPatch]
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthorized
    // TODO: return 400 if patch request is invalid
    // TODO: return 204 if patch successful
  }

  val runsUserIdRunIdGetOperation = (apiOperation[RunSummary]("runsUserIdRunIdGet")
    summary "Retrieves a run summary uploaded by the given user."
    notes
      """This endpoint retrieves a single run summary uploaded by the given user. Only administrators and the run
        |summary uploader can access this resource.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      pathParam[String]("runId").description("Run summary ID.")
    )
    responseMessages (
      StringResponseMessage(400, "User ID or run summary ID not specified."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, "User ID or run summary ID not found.")
    )
  )

  get("/users/:userId/:runId", operation(runsUserIdRunIdGetOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if user ID or run ID not found
    // TODO: return 401 if unauthorized
    // TODO: return 200 and run summary
  }

  val runsUserIdPostOperation = (apiOperation[RunSummary]("runsUserIdPost")
    summary "Uploads a JSON run summary."
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      queryParam[Boolean]("set-public")
        .description("Whether to set the run summary to public or not (default: false).")
        .optional,
      bodyParam[Any]("run-summary").description("Contents of the run summary.")
    )
    responseMessages (
      StringResponseMessage(201, "Run summary added."),
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(400, "Run summary is invalid."),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message)
    )
  )

  post("/users/:userId", operation(runsUserIdPostOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    val setPublic = params.getAs[Boolean]("set-public").getOrElse(false)
    val runSummary = parsedBody.extract[Any]
    // TODO: return 404 if user not found
    // TODO: return 401 if not authorized
    // TODO: return 400 if run summary is invalid
    // TODO: return 201 if post successful
  }

  val runsUserIdGetOperation = (apiOperation[List[RunSummary]]("runsUserIdGet")
    summary "Retrieves run summaries uploaded by the given user."
    notes
      """This endpoint retrieves run summaries uploaded by the given user sorted by last upload date first.
        |Only administrators and the run summary uploader can access this resource. Note that this endpoint omits the
        |actual run summary content. To retrieve the run summary content, you must specify its ID using another
        |endpoint.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("userId").description("Run summary uploader ID."),
      queryParam[String]("pipeline")
      .description(
        """Filters for summaries produced by the given pipeline. Valid values are `gentrap`, `unknown`. If not
          |specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
      .allowableValues(AllowedPipelineParams.toList)
      .optional
    )
    responseMessages (
      StringResponseMessage(400, CommonErrors.UnspecifiedUserId.message),
      StringResponseMessage(401, CommonErrors.Unauthorized.message),
      StringResponseMessage(404, CommonErrors.MissingUserId.message)
    )
  )

  get("/users/:userId", operation(runsUserIdGetOperation)) {
    val userId = params.getOrElse("userId", halt(400, CommonErrors.UnspecifiedUserId))
    // TODO: return 404 if user ID not found
    // TODO: return 401 if not authorized
    // TODO: return 200 and user's run summary
  }

  val runsPublicRunIdGetOperation = (apiOperation[List[RunSummary]]("runsPublicRunIdGet")
    summary "Retrieves a single public run summary."
    notes "This endpoint retrieves a single public run summary uploaded by a user."
    parameters pathParam[String]("runId").description("Run summary ID.")
    responseMessages (
      StringResponseMessage(400, CommonErrors.UnspecifiedRunId.message),
      StringResponseMessage(404, CommonErrors.MissingRunId.message)
    )
  )

  get("/:runId", operation(runsPublicRunIdGetOperation)) {
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return 404 if run ID not found
    // TODO: return 200 and public run summary
  }

  val runsPublicGetOperation = (apiOperation[List[RunSummary]]("runsPublicGet")
    summary "Retrieves public run summaries."
    notes
      """This endpoint retrieves run summaries which have been set to public by the uploader. The run summaries are
        |returned with the content field omitted.
      """.stripMargin.replaceAll("\n", "")
    parameters queryParam[String]("pipeline")
      .description(
        """Filters for summaries produced by the given pipeline. Valid values are `gentrap`, `unknown`. If not
          |specified, all run summaries are returned.""".stripMargin.replaceAll("\n", ""))
      .allowableValues(AllowedPipelineParams.toList)
      .optional
    responseMessages StringResponseMessage(400, CommonErrors.InvalidPipeline.message)
  )

  get("/", operation(runsPublicGetOperation)) {
    val pipeline = params.getAs[String]("pipeline")
      .foreach { case str => if (!AllowedPipelineParams.contains(str)) halt(400, CommonErrors.InvalidPipeline) }
    // TODO: return 200 and public run summaries
  }
}