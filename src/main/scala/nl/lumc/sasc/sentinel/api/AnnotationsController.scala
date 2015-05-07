package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.models._

class AnnotationsController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "Retrieval of annotation file synopses"
  override protected val applicationName: Option[String] = Some("annotations")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val annotationsRefIdGetOperation = (apiOperation[List[Annotation]]("annotationsRefIdGet")
    summary "Retrieves a single full annotation item."
    parameters pathParam[String]("annotId").description("Annotation ID query.")
    responseMessages (
      StringResponseMessage(400, "Annotation ID not specified."),
      StringResponseMessage(404, "Annotation ID can not be found."))
  )

  get("/:annotId", operation(annotationsRefIdGetOperation)) {
    val annotId = params.getAs[String]("annotId").getOrElse(halt(400, ApiError("Annotation ID not specified.")))
    // TODO: return 404 if annotation ID is not found
    // TODO: return 200 and annotation item
  }

  val annotationsGetOperation = (apiOperation[List[Annotation]]("annotationsGet")
    summary "Retrieves all available annotation items."
  )

  get("/", operation(annotationsGetOperation)) {
    // TODO: return 200 and annotation items
  }
}