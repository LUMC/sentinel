package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Controller for the `/annotations` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class AnnotationsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName = Some("annotations")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Retrieval of annotation file records"

  /** Annotation adapter for connecting to the database. */
  protected val annots = new AnnotationsAdapter { val mongo = self.mongo }

  // format: OFF
  val annotationsRefIdGetOperation = (apiOperation[Seq[AnnotationRecord]]("annotationsRefIdGet")
    summary "Retrieves a single full annotation item."
    parameters pathParam[String]("annotId").description("Annotation ID query.")
    responseMessages StringResponseMessage(404, "Annotation ID can not be found."))
  // format: ON

  get("/:annotId", operation(annotationsRefIdGetOperation)) {
    val errMsg = ApiMessage("Annotation ID can not be found.")
    val annotId = params("annotId")
      .getObjectId
      .getOrElse(halt(404, errMsg))
    annots.getAnnotation(annotId) match {
      case None        => NotFound(errMsg)
      case Some(annot) => Ok(annot)
    }
  }

  // format: OFF
  val annotationsGetOperation = (apiOperation[Seq[AnnotationRecord]]("annotationsGet")
    summary "Retrieves all available annotation items.")
  // format: ON

  get("/", operation(annotationsGetOperation)) {
    annots.getAnnotations()
  }
}
