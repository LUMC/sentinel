package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.{ AllowedLibTypeParams, AllowedAccLevelParams }
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.gentrap.GentrapAlignmentStats
import nl.lumc.sasc.sentinel.utils.splitParam

class StatsController(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "Statistics from deposited summaries"
  override protected val applicationName: Option[String] = Some("stats")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  val statsRunsGetOperation = (apiOperation[List[RunStats]]("statsRunsGet")
    summary "Retrieves general statistics of uploaded run summaries."
  )

  get("/runs", operation(statsRunsGetOperation)) {
    // TODO: return 200 and run stats
  }

  val statsAlignmentsGentrapGetOperation = (apiOperation[List[GentrapAlignmentStats]]("statsAlignmentsGentrapGet")
    summary "Retrieves the alignment statistics of Gentrap pipeline runs."
    parameters (
      queryParam[List[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("references")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("annotations")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("accLevel")
        .description(
          """The level at which the alignment statistics are gathered. Possible values are `lib` for library-level
            |accumulation or `sample` for sample-level accumulation.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedAccLevelParams.toList)
        .optional
    )
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidAccLevel.message),
      StringResponseMessage(404, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs not found.")
    )
  )

  get("/alignments/gentrap", operation(statsAlignmentsGentrapGetOperation)) {
    val runIds = splitParam(params.getAs[String]("runIds"))
    val references = splitParam(params.getAs[String]("references"))
    val annotations = splitParam(params.getAs[String]("annotations"))
    val accLevel = params.getAs[String]("accLevel")
    // TODO: return 400 if accumulation level is invalid
    // TODO: return 404 if run ID, ref ID, and/or annotID is not found
    // TODO: return 200 and alignment stats
  }

  val statsSequencesGentrapGetOperation = (apiOperation[List[SeqStats]]("statsSequencesGentrapGet")
    summary "Retrieves the sequencing statistics of Gentrap pipeline runs."
    parameters (
      queryParam[List[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("references")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("annotations")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            |libraries, `paired` for paired end libraries. If not specified, both library types are returned.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.toList)
        .optional
    )
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidLibType.message),
      StringResponseMessage(404, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs not found.")
    )
  )

  get("/sequences/gentrap", operation(statsSequencesGentrapGetOperation)) {
    val runIds = splitParam(params.getAs[String]("runIds"))
    val references = splitParam(params.getAs[String]("references"))
    val annotations = splitParam(params.getAs[String]("annotations"))
    val libType = params.getAs[String]("libType")
    // TODO: return 400 if library type is invalid
    // TODO: return 404 if run ID, ref ID, and/or annotID is not found
    // TODO: return 200 and sequence stats
  }
}