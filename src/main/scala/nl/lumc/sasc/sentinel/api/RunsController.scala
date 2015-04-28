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
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "Retrieval of public run summaries"
  override protected val applicationName: Option[String] = Some("runs")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val runsPublicRunIdGetOperation = (apiOperation[List[RunSummary]]("runsPublicRunIdGet")
    summary "Retrieves a single public run summary."
    notes "This endpoint retrieves a single public run summary uploaded by a user."
    parameters pathParam[String]("runId").description("Run summary ID.")
    responseMessages StringResponseMessage(404, "Run with specified ID not found.")
  )

  get("/:runId", operation(runsPublicRunIdGetOperation)) {
    val runId = params.getOrElse("runId", halt(400, CommonErrors.UnspecifiedRunId))
    // TODO: return public run summary
    // TODO: return 404 if run ID not found
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
    responseMessages StringResponseMessage(400, "Pipeline is invalid.")
  )

  get("/", operation(runsPublicGetOperation)) {
    val pipeline = params.getAs[String]("pipeline")
      .foreach { case str => if (!AllowedPipelineParams.contains(str)) halt(400, CommonErrors.InvalidPipeline) }
    // TODO: return public run summaries
  }
}