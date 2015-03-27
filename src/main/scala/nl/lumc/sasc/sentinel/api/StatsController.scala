package nl.lumc.sasc.sentinel.api

import nl.lumc.sasc.sentinel.model._
import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

class StatsController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "statistics from deposited summaries"
  override protected val applicationName: Option[String] = Some("stats")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val statsAlignmentsGentrapGetOperation = (apiOperation[List[GentrapAlignmentStats]]("statsAlignmentsGentrapGet")
    summary "Retrieves the alignment statistics for Gentrap pipeline runs."
  )

  get("/alignments/gentrap", operation(statsAlignmentsGentrapGetOperation)) {
  }

  val statsSequencesGentrapGetOperation = (apiOperation[List[GeneralSeqInput]]("statsSequencesGentrapGet")
    summary "Retrieves the sequencing statistics for Gentrap pipeline runs."
    parameters (
      queryParam[Boolean]("all").description("").optional,
      queryParam[List[String]]("references").description("").optional,
      queryParam[List[String]]("annotations").description("").optional,
      queryParam[String]("lib").description("").optional
    )
  )

  get("/sequences/gentrap", operation(statsSequencesGentrapGetOperation)) {
    val all = params.getAs[Boolean]("all")
    val referencesString = params.getAs[String]("references")
    val references = if ("pipes".equals("default")) {
      referencesString match {
        case Some(str) => str.split(",")
        case None      => List()
      }
    } else List()
    val annotationsString = params.getAs[String]("annotations")
    val annotations = if ("pipes".equals("default")) {
      annotationsString match {
        case Some(str) => str.split(",")
        case None      => List()
      }
    } else List()
    val lib = params.getAs[String]("lib")
    println("all: " + all)
    println("references: " + references)
    println("annotations: " + annotations)
    println("lib: " + lib)
  }
}