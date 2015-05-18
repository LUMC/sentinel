package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.ReferencesProcessor

class ReferencesController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet
    with JacksonJsonSupport
    with SwaggerSupport {

  protected val applicationDescription: String = "Retrieval of reference sequence synopses"
  override protected val applicationName: Option[String] = Some("references")

  protected val refs = new ReferencesProcessor(mongo)

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
    refs.getReference(refId) match {
      case None      => NotFound(ApiError("Reference ID can not be found."))
      case Some(ref) => Ok(ref)
    }
  }

  val referencesGetOperation = (apiOperation[List[Reference]]("referencesGet")
    summary "Retrieves all available reference items."
  )

  get("/", operation(referencesGetOperation)) {
    refs.getReferences()
  }
}