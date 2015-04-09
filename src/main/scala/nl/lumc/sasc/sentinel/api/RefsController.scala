package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.models._
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

class RefsController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "reference and annotation information"
  override protected val applicationName: Option[String] = None

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val referencesGetOperation = (apiOperation[List[AlignmentReference]]("referencesGet")
    summary "Retrieves all available reference items."
    )

  get("/", operation(referencesGetOperation)) {
  }

  val annotationsGetOperation = (apiOperation[List[AnnotationFile]]("annotationsGet")
    summary "Retrieves all available annotation items."
  )

  get("/annotations", operation(annotationsGetOperation)) {
  }
}