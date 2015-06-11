package nl.lumc.sasc.sentinel.api

import org.json4s._

import org.bson.types.ObjectId
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{ DataType, Model, SwaggerSupport }

import nl.lumc.sasc.sentinel.models.ApiMessage
import nl.lumc.sasc.sentinel.utils.{ SentinelJsonFormats, separateObjectIds, splitParam }

abstract class SentinelServlet extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = SentinelJsonFormats

  // NOTE: Java's MongoDB driver parses all MapReduce number results to Double, so we have to resort to this.
  protected def transformMapReduceResult(results: Any): JValue =
    Extraction.decompose(results)
      .transformField { case JField("count", JDouble(n)) => ("count", JInt(n.toLong)) }

  override protected def registerModel(model: Model): Unit = {
    // FIXME: This is a bit hackish, but scalatra-swagger does not make it clear how to intercept / prevent certain
    //        models from being exposed. Until they have an officially documented way of doing so, we'll stick with this.
    // Intercept ObjectId model creation, to prevent its internal attributes from being exposed in the API
    // We only want to show them as plain strings
    // Also, skip ObjectId creation completely
    if (model.id != "ObjectId") {
      val interceptedProp = model.properties.map {
        case (propName, prop) =>
          val interceptedProp = prop.`type` match {
            case vdt: DataType.ValueDataType if vdt.name == "ObjectId" =>
              prop.copy(`type` = DataType.String)
            case cdt: DataType.ContainerDataType if cdt.typeArg.isDefined && cdt.typeArg.get.name == "ObjectId" =>
              cdt.name match {
                case "List"    => prop.copy(`type` = DataType.GenList(DataType.String))
                case "Set"     => prop.copy(`type` = DataType.GenSet(DataType.String))
                case otherwise => prop
              }
            case otherwise => prop
          }
          (propName, interceptedProp)
      }
      val newModel =
        if (model.id == "RunDocument")
          model.copy(properties = interceptedProp.filter {
            case (propName, prop) => propName != "sampleIds" || propName != "samples"
          })
        else
          model.copy(properties = interceptedProp)
      super.registerModel(newModel)
    }
  }

  protected def getObjectIds(strs: Seq[String], msg: Option[ApiMessage] = None): Seq[ObjectId] = {
    val (validIds, invalidIds) = separateObjectIds(strs)
    if (invalidIds.nonEmpty) msg match {
      case None    => halt(400)
      case Some(m) => halt(400, m.copy(data = Map("invalid" -> invalidIds)))
    }
    else validIds
  }

  protected def getRunObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid run ID(s) provided.")))

  protected def getRefObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid reference ID(s) provided.")))

  protected def getAnnotObjectIds(rawParam: Option[String]): Seq[ObjectId] = getObjectIds(
    splitParam(rawParam), Option(ApiMessage("Invalid annotation ID(s) provided.")))

  options("/*") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  after() {
    response.setHeader("Set-Cookie", null) // Disable cookies ~ the server should not store state
    response.setHeader("REMOTE_USER", null) // Remove nonstandard field added automatically by Scalatra
  }
}
