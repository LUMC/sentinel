/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.models

import scala.language.reflectiveCalls

import org.json4s._
import org.json4s.JsonDSL._

import nl.lumc.sasc.sentinel.utils.SentinelJsonFormats

object JsonPatch {

  private val pathSep: String = "/"

  private def mkPath(toks: Seq[String]): String = s"$pathSep${toks.mkString(pathSep)}"

  /** Base trait for JSON patch operations. See http://jsonpatch.com/ for more information. */
  sealed trait PatchOp {
    /** Patch operation name. */
    def op: String

    /** Path of the value to patch. */
    def path: String

    /** Helper method for getting the tokens of the path. */
    val pathTokens: List[String] = path.split(pathSep).filter(_.nonEmpty).toList

    /** Creates a JValue object of this patch. */
    def toJValue: JObject = ("op", op) ~ ("path", path)

    /** Creates a new PatchOp object with the given path tokens. */
    def updatePath(toks: Seq[String]) = this match {
      case p: AddOp     => p.copy(path = mkPath(toks))
      case p: CopyOp    => p.copy(path = mkPath(toks))
      case p: MoveOp    => p.copy(path = mkPath(toks))
      case p: RemoveOp  => p.copy(path = mkPath(toks))
      case p: ReplaceOp => p.copy(path = mkPath(toks))
      case p: TestOp    => p.copy(path = mkPath(toks))
    }
  }

  /** Trait for JSON patch objects with a 'value' attribute. */
  sealed trait PatchOpWithValue extends PatchOp {

    def value: Any

    abstract override def toJValue: JObject = JObject(
      super.toJValue.obj :+ JField("value", Extraction.decompose(value)(SentinelJsonFormats)))

    /** Returns the value if it is either string, int, long, double, or boolean. Nulls, arrays, or other objects return None. */
    def atomicValue: Option[Any] = value match {
      case i: Int                             => Option(i)
      case l: Long                            => Option(l)
      case i: BigInt if i.isValidInt          => Option(i.intValue)
      case i: BigInt if i.isValidLong         => Option(i.longValue)
      case d: Double                          => Option(d)
      case d: BigDecimal if d.isDecimalDouble => Option(d.toDouble)
      case s: String                          => Option(s)
      case b: Boolean                         => Option(b)
      case otherwise                          => None
    }
  }

  /** Trait for JSON patch objects with a 'from' attribute. */
  sealed trait PatchOpWithFrom extends PatchOp {

    def from: String

    abstract override def toJValue: JObject = JObject(super.toJValue.obj :+ JField("from", from))
  }

  /** The JSON patch 'add' operation. */
  final case class AddOp(path: String, value: Any) extends PatchOpWithValue { val op = "add" }

  /** The JSON patch 'copy' operation. */
  final case class CopyOp(path: String, from: String) extends PatchOpWithFrom { val op = "copy" }

  /** The JSON patch 'move' operation. */
  final case class MoveOp(path: String, from: String) extends PatchOpWithFrom { val op = "move" }

  /** The JSON patch 'remove' operation. */
  final case class RemoveOp(path: String) extends PatchOp { val op = "remove" }

  /** The JSON patch 'replace' operation. */
  final case class ReplaceOp(path: String, value: Any) extends PatchOpWithValue { val op = "replace" }

  /** The JSON patch 'test' operation. */
  final case class TestOp(path: String, value: Any) extends PatchOpWithValue { val op = "test" }

  object PatchOp {

    /** Attributes that are hidden from the Swagger spec. */
    val hiddenAttributes: Set[String] = Set("pathTokens", "toJValue", "updatePathTokens")

    /** Creates a patch object, if possible, from the given JValue object. */
    def fromJson(jv: JValue)(implicit formats: Formats): Option[PatchOp] = jv \ "op" match {
      case JString("add")     => jv.extractOpt[AddOp]
      case JString("remove")  => jv.extractOpt[RemoveOp]
      case JString("replace") => jv.extractOpt[ReplaceOp]
      case JString("copy")    => jv.extractOpt[CopyOp]
      case JString("move")    => jv.extractOpt[MoveOp]
      case JString("test")    => jv.extractOpt[TestOp]
      case otherwise          => None
    }
  }
}
