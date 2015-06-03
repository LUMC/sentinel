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
        .optional,
      queryParam[String]("libType")
        .description(
          """The types of sequence libraries to return. Possible values are `single` for single end
            | libraries and `paired` for paired end libraries (default: `paired`). This parameter is ignored if
            | `accLevel` is set to `sample` since a sample may contain mixed library types.
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional)
    responseMessages StringResponseMessage(400, CommonErrors.InvalidAccLevel.message))
  // format: ON

  get("/alignments/gentrap", operation(statsAlignmentsGentrapGetOperation)) {
    val runIds = splitParam(params.getAs[String]("runIds"))
    val refIds = splitParam(params.getAs[String]("refIds"))
    val annotIds = splitParam(params.getAs[String]("annotIds"))
    val accLevel = params.getAs[String]("accLevel").getOrElse(AccLevel.Sample.toString)
    val libType = params.getAs[String]("libType").getOrElse(LibType.Paired.toString)
    // TODO: return 404 if run ID, ref ID, and/or annotID is not found

    val acc = AllowedAccLevelParams.getOrElse(accLevel, halt(400, CommonErrors.InvalidAccLevel))
    val lib = AllowedLibTypeParams.getOrElse(libType, halt(400, CommonErrors.InvalidLibType))

    gentrap.getAlignmentStats(acc, runIds, refIds, annotIds)
  }

  // format: OFF
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
            | libraries and `paired` for paired end libraries (default: `paired`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedLibTypeParams.keySet.toList)
        .optional,
      queryParam[String]("qcPhase")
        .description(
          """Selects for the sequencing QC phase to return. Possible values are `raw` for raw data before any QC
            | is done and `processed` for sequencing statistics just before alignment (default: `raw`).
          """.stripMargin.replaceAll("\n", ""))
        .allowableValues(AllowedSeqQcPhaseParams.keySet.toList)
        .optional)
    responseMessages (
      StringResponseMessage(400, CommonErrors.InvalidLibType.message),
      StringResponseMessage(400, CommonErrors.InvalidSeqQcPhase.message),
      StringResponseMessage(400, "One or more of the supplied run IDs, reference IDs, and/or annotation IDs is invalid.")))
  // format: ON

  get("/sequences/gentrap", operation(statsSequencesGentrapGetOperation)) {
    val (runIds, invalidRunIds) = separateObjectIds(splitParam(params.getAs[String]("runIds")))
    if (invalidRunIds.nonEmpty)
      halt(400, ApiMessage("Invalid run ID(s) provided.", Map("invalid" -> invalidRunIds)))

    val (refIds, invalidRefIds) = separateObjectIds(splitParam(params.getAs[String]("refIds")))
    if (invalidRefIds.nonEmpty)
      halt(400, ApiMessage("Invalid reference ID(s) provided.", Map("invalid" -> invalidRefIds)))

    val (annotIds, invalidAnnotIds) = separateObjectIds(splitParam(params.getAs[String]("annotIds")))
    if (invalidAnnotIds.nonEmpty)
      halt(400, ApiMessage("Invalid annotation ID(s) provided.", Map("invalid" -> invalidAnnotIds)))

    val libType = params.getAs[String]("libType").getOrElse(LibType.Paired.toString)
    val lib = AllowedLibTypeParams.getOrElse(libType, halt(400, CommonErrors.InvalidLibType))

    val seqQcPhase = params.getAs[String]("qcPhase").getOrElse(SeqQcPhase.Raw.toString)
    val qc = AllowedSeqQcPhaseParams.getOrElse(seqQcPhase, halt(400, CommonErrors.InvalidSeqQcPhase))

    gentrap.getSeqStats(lib, qc, runIds, refIds, annotIds)
  }
}
