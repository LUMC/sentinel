/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
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
package nl.lumc.sasc.sentinel.adapters

import java.io.ByteArrayInputStream
import scala.util.Try
import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.jackson.JsonMethods.parse
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.Perhaps
import nl.lumc.sasc.sentinel.models.{ Payloads, SinglePathPatch }, Payloads._
import nl.lumc.sasc.sentinel.utils.{ getResourceStream, JsonValidator, SentinelJsonFormats }
import nl.lumc.sasc.sentinel.utils.Implicits._

/** Trait for parsing JSON. */
trait JsonAdapter {

  /** JSON format for Sentinel. */
  implicit val formats = SentinelJsonFormats

  /**
   * Parses the given byte array into JSON.
   *
   * @param contents Raw bytes to parse.
   * @return JValue object.
   */
  def extractJson(contents: Array[Byte]): Perhaps[JValue] =
    Try(parse(new ByteArrayInputStream(contents))) match {
      case scala.util.Failure(_)  => JsonValidationError("File is not JSON.").left
      case scala.util.Success(jv) => jv.right
    }
}

/** Trait for validating input JSON with a schema. */
trait JsonValidationAdapter extends JsonAdapter {

  /** Resource URLs for JSON schema file. */
  def jsonSchemaUrls: Seq[String]

  /** Parses the given byte array into as a JSON file and validates it. */
  val extractAndValidateJson =
    // The compiler actually doesn't need these type annotations, but some IDE does.
    Kleisli[Perhaps, Array[Byte], JValue] { extractJson } >=>
      Kleisli[Perhaps, JValue, JValue] { validateJson(jsonValidators) }

  /**
   * Checks whether a parsed JSON value fulfills the given schemas or not.
   *
   * @param json Input JSON value.
   * @return Validation result.
   */
  def validateJson(validators: Seq[JsonValidator])(json: JValue): Perhaps[JValue] = {
    val errMsgs = validators.par
      .flatMap { vl =>
        vl.validate(json).iterator().map(_.toString).toList
      }.seq
    if (errMsgs.isEmpty) json.right
    else JsonValidationError(errMsgs).left
  }

  /** JSON validators. */
  final lazy val jsonValidators: Seq[JsonValidator] = jsonSchemaUrls
    .map { jsu => JsonValidator(getResourceStream(jsu)) }
}

/**
 * Trait for parsing and validating JSON patch inputs
 *
 * At the moment, we only support patches with single paths (`add`, `replace`, and `remove`).
 *
 * See http://jsonpatch.com/ for the JSON patch specification.
 */
trait SinglePathPatchJsonAdapter extends JsonValidationAdapter {

  /** Type alias for the patch validation function, which is a function that takes patches and return its ValidationNEL. */
  type ValidationFunc = Seq[SinglePathPatch] => ValidationNel[String, Seq[SinglePathPatch]]

  /** Type alias for patches. */
  private type Patches = Seq[SinglePathPatch]

  /** Patch schema URL. */
  final val jsonSchemaUrls = Seq("/schemas/json_patch.json")

  /**
   * Extracts the given byte array into patch objects.
   *
   * @param contents Raw byte contents to extract.
   * @return Patch operations or an error message-containing payload for user display.
   */
  def extractPatches(contents: Array[Byte]): Perhaps[Seq[SinglePathPatch]] =
    for {
      // Make sure incoming data is JArray ~ so we wrap single item in a list
      json <- extractAndValidateJson(contents).map {
        case single @ JObject(_) => JArray(List(single))
        case otherwise           => otherwise
      }
      patchOps <- json.extract[Seq[SinglePathPatch]].right
    } yield patchOps

  /** Validation functions to apply to incoming patches. */
  def patchValidationFuncs: Seq[ValidationFunc] = Seq(mustBeNonEmpty, mustBeSupportedOp)

  /**
   * Using the validation function(s), validate the given patch operations.
   *
   * @param ops Patch operations to validate.
   * @return Patch operations or an error message-containing payload for user display.
   */
  def validatePatches(ops: Seq[SinglePathPatch]): Perhaps[Seq[SinglePathPatch]] = {
    // Perform validation in parallel for each validation function and aggregate any errors
    val vRes = patchValidationFuncs.par
      .map { vfunc => vfunc(ops) }
      .reduceLeft { _ <@> _ }
    vRes match {
      case Failure(f) => PatchValidationError(f.toList).left
      case Success(s) => s.right
    }
  }

  /** Function for extracting then validating patches. */
  val extractAndValidatePatches =
    // The compiler actually doesn't need these type annotations, but some IDE does.
    Kleisli[Perhaps, Array[Byte], Patches] { extractPatches } >=>
      Kleisli[Perhaps, Patches, Patches] { validatePatches }

  /** Valid paths for the patch operation. */
  protected val validOps: Set[String] = Set("add", "remove", "replace")

  /** Validation function that ensures there is at least one patch operation. */
  protected final val mustBeNonEmpty: ValidationFunc =
    (ops: Seq[SinglePathPatch]) => {
      if (ops.isEmpty) "Patch operations can not be empty.".failNel
      else ops.successNel
    }

  /** Validation function that ensures all the operations from the given patches are supported. */
  protected final val mustBeSupportedOp: ValidationFunc =
    (ops: Seq[SinglePathPatch]) =>
      ops
        .flatMap { op => if (!validOps(op.op)) Seq(s"Unsupported operation: '${op.op}'.") else Seq.empty[String] }
        .toList.toNel match {
          case Some(nel) => nel.fail
          case None      => ops.successNel
        }
}
