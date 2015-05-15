package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.processors.AnnotationsProcessor
import org.json4s._
import org.json4s.mongo.ObjectIdSerializer
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models._

class AnnotationsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends ScalatraServlet
    with JacksonJsonSupport
    with SwaggerSupport {

  protected val applicationDescription: String = "Retrieval of annotation file synopses"
  override protected val applicationName = Some("annotations")

  protected val annots = new AnnotationsProcessor(mongo)

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats + new ObjectIdSerializer

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
    annots.getAnnotation(annotId) match {
      case None        => NotFound(ApiError("Annotation ID can not be found."))
      case Some(annot) => Ok(annot)
    }
  }

  val annotationsGetOperation = (apiOperation[List[Annotation]]("annotationsGet")
    summary "Retrieves all available annotation items."
  )

  get("/", operation(annotationsGetOperation)) {
    annots.getAnnotations()
  }
}