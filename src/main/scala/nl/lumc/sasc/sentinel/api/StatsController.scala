package nl.lumc.sasc.sentinel.api

import scala.util.Try

import org.scalatra.servlet.FileItem
import org.scalatra.swagger._

import nl.lumc.sasc.sentinel._
import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.gentrap._
import nl.lumc.sasc.sentinel.utils.{ separateObjectIds, splitParam }

class StatsController(implicit val swagger: Swagger, mongo: MongodbAccessObject) extends SentinelServlet { self =>

  protected val applicationDescription: String = "Statistics from deposited summaries"
  override protected val applicationName: Option[String] = Some("stats")

  protected val runs = new RunsAdapter {
    val mongo = self.mongo
    def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) = Try(throw new NotImplementedError)
  }
  protected val gentrap = new GentrapOutputProcessor(mongo)

  val statsRunsGetOperation = (apiOperation[Seq[PipelineRunStats]]("statsRunsGet")
    summary "Retrieves general statistics of uploaded run summaries."
  )

  get("/runs", operation(statsRunsGetOperation)) {
    runs.getGlobalRunStats()
  }

  // format: OFF
  val statsGentrapAlignmentsGetOperation = (apiOperation[Seq[GentrapAlignmentStats]]("statsGentrapAlignmentsGet")
    summary "Retrieves the alignment statistics of Gentrap pipeline runs."
    parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("accLevel")
        .description(
          """The level at which the alignment statistics are gathered. Possible values are `lib` for library-level
            | accumulation or `sample` for sample-level accumulation (default: `sample`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedAccLevelParams.keySet.toList)
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included. This
            | parameter is considered to be unset if `accLevel` is set to `sample` regardless of user input since a
            | single sample may contain mixed library types.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[Boolean]("sorted")
      .description(
        """Whether to try sort the returned items or not. If set to false, the returned
          | results are randomized. If set to true, the returned items are guaranteed to be sorted. The sort order is
          | most often the items' creation time (most recent first), though this is not guaranteed (default: `false`).
        """.stripMargin.replaceAll("\n", ""))
      .optional)
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidAccLevel.message),
      StringResponseMessage(400, CommonErrors.InvalidLibType.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/gentrap/alignments", operation(statsGentrapAlignmentsGetOperation)) {

    val sorted = params.get("sorted") match {
      case None => false
      case Some(p) => p.toLowerCase match {
        case "0" | "no" | "false" | "null" | "none" | "nothing" => false
        case otherwise => true
      }
    }

    val (runIds, invalidRunIds) = separateObjectIds(splitParam(params.getAs[String]("runIds")))
    if (invalidRunIds.nonEmpty)
      halt(400, ApiMessage("Invalid run ID(s) provided.", Map("invalid" -> invalidRunIds)))

    val (refIds, invalidRefIds) = separateObjectIds(splitParam(params.getAs[String]("refIds")))
    if (invalidRefIds.nonEmpty)
      halt(400, ApiMessage("Invalid reference ID(s) provided.", Map("invalid" -> invalidRefIds)))

    val (annotIds, invalidAnnotIds) = separateObjectIds(splitParam(params.getAs[String]("annotIds")))
    if (invalidAnnotIds.nonEmpty)
      halt(400, ApiMessage("Invalid annotation ID(s) provided.", Map("invalid" -> invalidAnnotIds)))

    val accLevel = params.getAs[String]("accLevel").getOrElse(AccLevel.Sample.toString)
    val acc = AllowedAccLevelParams.getOrElse(accLevel, halt(400, CommonErrors.InvalidAccLevel))

    val libType = params.getAs[String]("libType")
      .collect { case lib => AllowedLibTypeParams.getOrElse(lib, halt(400, CommonErrors.InvalidLibType)) }

    gentrap.getAlignmentStats(acc, libType, runIds, refIds, annotIds, sorted)
  }

  // format: OFF
  val statsGentrapSequencesGetOperation = (apiOperation[Seq[SeqStats]]("statsGentrapSequencesGet")
    summary "Retrieves the sequencing statistics of Gentrap pipeline runs."
    parameters (
      queryParam[Seq[String]]("runIds")
        .description("Include only Gentrap runs with the given run ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("refIds")
        .description("Include only Gentrap runs based on the given reference ID(s).")
        .multiValued
        .optional,
      queryParam[Seq[String]]("annotIds")
        .description("Include only Gentrap runs that uses at least one of the given annotation ID(s).")
        .multiValued
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries or `paired` for paired end libraries. If not set, both library types are included.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[String]("qcPhase")
        .description(
          """Selects for the sequencing QC phase to return. Possible values are `raw` for raw data before any QC
            | is done and `processed` for sequencing statistics just before alignment (default: `raw`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedSeqQcPhaseParams.keySet.toList)
        .optional,
      queryParam[Boolean]("sorted")
        .description(
          """Whether to try sort the returned items or not. If set to false, the returned
            | results are randomized. If set to true, the returned items are guaranteed to be sorted. The sort order is
            | most often the items' creation time (most recent first), though this is not guaranteed (default: `false`).
          """.stripMargin.replaceAll("\n", ""))
        .optional)
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidLibType.message),
      StringResponseMessage(400, CommonErrors.InvalidSeqQcPhase.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/gentrap/sequences", operation(statsGentrapSequencesGetOperation)) {

    val sorted = params.get("sorted") match {
      case None => false
      case Some(p) => p.toLowerCase match {
        case "0" | "no" | "false" | "null" | "none" | "nothing" => false
        case otherwise => true
      }
    }

    val (runIds, invalidRunIds) = separateObjectIds(splitParam(params.getAs[String]("runIds")))
    if (invalidRunIds.nonEmpty)
      halt(400, ApiMessage("Invalid run ID(s) provided.", Map("invalid" -> invalidRunIds)))

    val (refIds, invalidRefIds) = separateObjectIds(splitParam(params.getAs[String]("refIds")))
    if (invalidRefIds.nonEmpty)
      halt(400, ApiMessage("Invalid reference ID(s) provided.", Map("invalid" -> invalidRefIds)))

    val (annotIds, invalidAnnotIds) = separateObjectIds(splitParam(params.getAs[String]("annotIds")))
    if (invalidAnnotIds.nonEmpty)
      halt(400, ApiMessage("Invalid annotation ID(s) provided.", Map("invalid" -> invalidAnnotIds)))

    val libType = params.getAs[String]("libType")
      .collect { case lib => AllowedLibTypeParams.getOrElse(lib, halt(400, CommonErrors.InvalidLibType)) }

    val seqQcPhase = params.getAs[String]("qcPhase").getOrElse(SeqQcPhase.Raw.toString)
    val qc = AllowedSeqQcPhaseParams.getOrElse(seqQcPhase, halt(400, CommonErrors.InvalidSeqQcPhase))

    gentrap.getSeqStats(libType, qc, runIds, refIds, annotIds, sorted)
  }
}
