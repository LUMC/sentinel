package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.implicits._

/**
 * Controller for the `/references` endpoint.
 *
 * @param swagger Container for main Swagger specification.
 * @param mongo Object for accessing the database.
 */
class ReferencesController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  /** Controller name, shown in the generated Swagger spec. */
  override protected val applicationName: Option[String] = Some("references")

  /** Controller description, shown in the generated Swagger spec. */
  protected val applicationDescription: String = "Retrieval of reference sequence records"

  /** Annotation adapter for connecting to the database. */
  protected val refs = new ReferencesAdapter { val mongo = self.mongo }

  // format: OFF
  val referencesRefIdGetOperation = (apiOperation[Seq[ReferenceRecord]]("referencesRefIdGet")
    summary "Retrieves a single reference record."
    parameters pathParam[String]("refId").description("Reference ID query.")
    responseMessages StringResponseMessage(404, "Reference ID can not be found."))
  // format: ON

  get("/:refId", operation(referencesRefIdGetOperation)) {
    val errMsg = ApiMessage("Reference ID can not be found.")
    val refId = params("refId")
      .getObjectId
      .getOrElse(halt(404, errMsg))
    refs.getReference(refId) match {
      case None      => NotFound(ApiMessage("Reference ID can not be found."))
      case Some(ref) => Ok(ref)
    }
  }

  // format: OFF
  val referencesGetOperation = (apiOperation[ReferenceRecord]("referencesGet")
    summary "Retrieves all available reference records.")
  // format: ON

  get("/", operation(referencesGetOperation)) {
    refs.getReferences()
  }
}