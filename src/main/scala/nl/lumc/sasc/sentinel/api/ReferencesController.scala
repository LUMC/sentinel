package nl.lumc.sasc.sentinel.api

import org.scalatra._
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.utils.implicits._

class ReferencesController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  protected val applicationDescription: String = "Retrieval of reference sequence records"
  override protected val applicationName: Option[String] = Some("references")

  protected val refs = new ReferencesAdapter { val mongo = self.mongo }

  val referencesRefIdGetOperation = (apiOperation[Seq[Reference]]("referencesRefIdGet")
    summary "Retrieves a single reference record."
    parameters pathParam[String]("refId").description("Reference ID query.")
    responseMessages StringResponseMessage(404, "Reference ID can not be found.")
  )

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

  val referencesGetOperation = (apiOperation[Reference]("referencesGet")
    summary "Retrieves all available reference records."
  )

  get("/", operation(referencesGetOperation)) {
    refs.getReferences()
  }
}