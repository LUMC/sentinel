package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._

class AnnotationsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  protected val applicationDescription: String = "Retrieval of annotation file synopses"
  override protected val applicationName = Some("annotations")

  protected val annots = new AnnotationsAdapter { val mongo = self.mongo }

  val annotationsRefIdGetOperation = (apiOperation[List[Annotation]]("annotationsRefIdGet")
    summary "Retrieves a single full annotation item."
    parameters pathParam[String]("annotId").description("Annotation ID query.")
    responseMessages StringResponseMessage(404, "Annotation ID can not be found.")
  )

  get("/:annotId", operation(annotationsRefIdGetOperation)) {
    annots.getAnnotation(params.getAs[String]("annotId").get) match {
      case None        => NotFound(ApiMessage("Annotation ID can not be found."))
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
