package nl.lumc.sasc.sentinel.api

import org.json4s._
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel.{ AllowedLibTypeParams, AllowedAccLevelParams }
import nl.lumc.sasc.sentinel.db.MongodbAccessObject
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.gentrap._
import nl.lumc.sasc.sentinel.utils.splitParam

class StatsController(mongo: MongodbAccessObject)(implicit val swagger: Swagger) extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  protected val applicationDescription: String = "Statistics from deposited summaries"
  override protected val applicationName: Option[String] = Some("stats")

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats

  val gentrap = new GentrapOutputProcessor(mongo)

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
      queryParam[List[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("accLevel")
        .description(
          """The level at which the alignment statistics are gathered. Possible values are `lib` for library-level
            | accumulation or `sample` for sample-level accumulation (default: `sample`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedAccLevelParams.keySet.toList)
        .optional
    )
    responseMessages StringResponseMessage(400, CommonErrors.InvalidAccLevel.message)
  )

  get("/alignments/gentrap", operation(statsAlignmentsGentrapGetOperation)) {
    val runIds = splitParam(params.getAs[String]("runIds"))
    val refIds = splitParam(params.getAs[String]("refIds"))
    val annotIds = splitParam(params.getAs[String]("annotIds"))
    val accLevel = params.getAs[String]("accLevel").getOrElse("sample")
    // TODO: return 404 if run ID, ref ID, and/or annotID is not found

    AllowedAccLevelParams.get(accLevel) match {
      case None           => BadRequest(CommonErrors.InvalidAccLevel)
      case Some(accEnum)  =>
        gentrap.getAlignmentStats(accEnum, runIds, refIds, annotIds)
    }
  }

  val statsSequencesGentrapGetOperation = (apiOperation[List[SeqStats]]("statsSequencesGentrapGet")
    summary "Retrieves the sequencing statistics of Gentrap pipeline runs."
    parameters (
      queryParam[List[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[List[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries, `paired` for paired end libraries. If not specified, both library types are returned.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional
    )
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidLibType.message),
      StringResponseMessage(404, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs not found.")
    )
  )

  get("/sequences/gentrap", operation(statsSequencesGentrapGetOperation)) {
    val runIds = splitParam(params.getAs[String]("runIds"))
    val refIds = splitParam(params.getAs[String]("refIds"))
    val annotIds = splitParam(params.getAs[String]("annotIds"))
    val libType = params.getAs[String]("libType")
    // TODO: return 400 if library type is invalid
    // TODO: return 404 if run ID, ref ID, and/or annotID is not found
    // TODO: return 200 and sequence stats
  }
}