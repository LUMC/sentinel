package nl.lumc.sasc.sentinel.api

import org.scalatra.ScalatraServlet
import org.scalatra.swagger._
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.FileUploadSupport

import nl.lumc.sasc.sentinel.model._

class DepotController(implicit val swagger: Swagger) extends ScalatraServlet
  with FileUploadSupport
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "submission and retrieval of raw summary files"
  override protected val applicationName: Option[String] = Some("depot")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val depotUserIdGetOperation = (apiOperation[List[DepotItem]]("depotUserIdGet")
    summary "Retrieves the items in the depot belonging to the specified user."
    notes
      """This endpoint retrieves depot items created by the given user sorted by last upload date first.
        |By default, 10 items are returned in a single response. A maximum of 100 items may be returned.
        |If the limit is specified above 100, only the IDs and creation times of the depot items will be
        |returned. In this case, the `exclude` parameter must also be specified.
      """.stripMargin.replaceAll("\n", "")
    parameters (
      pathParam[String]("user-id").description("Depot owner ID."),
      queryParam[Int]("max").description("Maximum number of depot items to return.").optional,
      queryParam[Boolean]("exclude").description("Whether to exclude depot item contents or not.").optional
    )
  )

  get("/{user-id}", operation(depotUserIdGetOperation)) {
    val userId = params.getOrElse("user-id", halt(400))
    val max = params.getAs[Int]("max")
    val exclude = params.getAs[Boolean]("exclude")
    println("user-id: " + userId)
    println("max: " + max)
    println("exclude: " + exclude)
  }

  val depotUserIdPostOperation = (apiOperation[Object]("depotUserIdPost")
    summary "Adds a custom JSON entry to the user depot."
    parameters (
      pathParam[String]("user-id").description(""),
      queryParam[String]("pipeline").description("").optional
    )
  )

  post("/{user-id}", operation(depotUserIdPostOperation)) {
    val userId = params.getOrElse("user-id", halt(400))
    val pipeline = params.getAs[String]("pipeline")
    println("user-id: " + userId)
    println("pipeline: " + pipeline)
  }

  val publicDepotGetOperation = (apiOperation[List[DepotItem]]("publicDepotGet")
    summary "Retrieves items in the public depot."
    notes
      """This endpoint retrieves depot items submitted anonymously, sorted by last upload date first. By default,
        |5 items are returned in a single response. A maximum of 50 items may be returned.
      """.stripMargin.replaceAll("\n", "")
    parameters queryParam[Int]("max").description("").optional
  )

  get("/public", operation(publicDepotGetOperation)) {
    val max = params.getAs[Int]("max")
    println("max: " + max)
  }

  val publicDepotPostOperation = (apiOperation[Object]("publicDepotPost")
    summary "Adds a custom JSON entry to the public depot."
    parameters queryParam[String]("pipeline").description("Biopet pipeline name that produces the depot item.").optional
  )

  post("/public", operation(publicDepotPostOperation)) {
    val pipeline = params.getAs[String]("pipeline")
    println("pipeline: " + pipeline)
  }
}