package nl.lumc.sasc.sentinel.api

import org.json4s._

import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{ DataType, Model, SwaggerSupport }

import nl.lumc.sasc.sentinel.utils.CustomObjectIdSerializer

abstract class SentinelServlet extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {

  override def render(value: JValue)(implicit formats: Formats = DefaultFormats): JValue =
    formats.emptyValueStrategy.replaceEmpty(value)

  protected implicit val jsonFormats: Formats = DefaultFormats + new CustomObjectIdSerializer

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
      super.registerModel(model.copy(properties = interceptedProp))
    }
  }

  options("/*") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }
}
