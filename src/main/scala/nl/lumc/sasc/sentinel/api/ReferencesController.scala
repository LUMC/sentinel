package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.models._
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

class ReferencesController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "Retrieval of reference sequence synopses"
  override protected val applicationName: Option[String] = None

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val referencesRefIdGetOperation = (apiOperation[List[Reference]]("referencesRefIdGet")
    summary "Retrieves a single full reference item."
    parameters pathParam[String]("refId").description("Reference ID query.")
    responseMessages (
      StringResponseMessage(400, "Reference ID not specified."),
      StringResponseMessage(404, "Reference ID can not be found."))
  )

  get("/:refId", operation(referencesRefIdGetOperation)) {
    val refId = params.getAs[String]("refId").getOrElse(halt(400, ApiError("Reference ID not specified.")))
    // TODO: return 404 if reference ID is not found
    // TODO: return 200 and reference item
  }

  val referencesGetOperation = (apiOperation[List[Reference]]("referencesGet")
    summary "Retrieves all available reference items."
  )

  get("/", operation(referencesGetOperation)) {
    // TODO: return 200 and reference items
  }
}