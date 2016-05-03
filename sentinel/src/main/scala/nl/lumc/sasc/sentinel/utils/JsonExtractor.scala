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
package nl.lumc.sasc.sentinel.utils

import java.io.ByteArrayInputStream
import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.jackson.JsonMethods.parseOpt
import org.json4s.jackson.Serialization.write
import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.models.JsonPatch.PatchOp
import nl.lumc.sasc.sentinel.models.Payloads._

/** Trait for parsing JSON. */
trait JsonExtractor {

  /** JSON format for Sentinel. */
  implicit val formats = SentinelJsonFormats

  /**
   * Parses the given byte array into JSON.
   *
   * @param contents Raw bytes to parse.
   * @return JValue object or an ApiPayload with error messages.
   */
  def extractJson(contents: Array[Byte]): Perhaps[JValue] =
    if (contents.isEmpty) JsonValidationError("Nothing to parse.").left
    else parseOpt(new ByteArrayInputStream(contents)) match {
      case None     => JsonValidationError("Invalid syntax.").left
      case Some(jv) => jv.right
    }
}

/** Trait for validating input JSON with a schema. */
trait ValidatedJsonExtractor extends JsonExtractor {

  /** Resource URLs for JSON schema file. */
  def jsonSchemaUrls: Seq[String]

  /** Parses the given byte array into as a JSON file and validates it. */
  def extractAndValidateJson(contents: Array[Byte]): Perhaps[JValue] = for {
    json <- extractJson(contents)
    validatedJson <- validateJson(json)
  } yield validatedJson

  /** Validates the given `JValue`. */
  val validateJson: JValue => Perhaps[JValue] = partialValidateJson(jsonValidators)

  /**
   * Checks whether a parsed JSON value fulfills the given schemas or not.
   *
   * @param json Input JSON value.
   * @return Validation result.
   */
  private[sasc] def partialValidateJson(validators: Seq[JsonValidator])(json: JValue): Perhaps[JValue] = {
    val errMsgs = validators.par
      .flatMap { vl =>
        vl.validate(json).iterator().map(_.toString).toList
      }.seq
    if (errMsgs.isEmpty) json.right
    else JsonValidationError(errMsgs).left
  }

  /** JSON validators. */
  private[sasc] lazy val jsonValidators: Seq[JsonValidator] = {
    val perhapsValidators = jsonSchemaUrls
      .map { jsu =>
        getResourceStream(jsu) match {
          case Some(s) => JsonValidator(s).right
          case None    => jsu.left
        }
      }
    // extract created validators and possible missing urls
    val (validators, missingUrls) = perhapsValidators.partition(_.isRight) match {
      case (vs, us) => (vs.collect { case \/-(v) => v }, us.collect { case -\/(u) => u })
    }
    if (missingUrls.nonEmpty) {
      val errMsg = s"Required schema URLs not found: '${missingUrls.mkString("', '")}'."
      // Here we really want to throw an exception since any missing URLs should trigger a failure during instantiation
      throw new IllegalStateException(errMsg)
    }
    validators
  }
}

/**
 * Object for parsing and validating JSON patch inputs
 *
 * See http://jsonpatch.com/ for the JSON patch specification.
 */
object JsonPatchExtractor extends ValidatedJsonExtractor {

  /** Patch schema URL. */
  final val jsonSchemaUrls = Seq("/schemas/json_patch.json")

  /**
   * Extracts the given byte array into patch objects.
   *
   * @param contents Raw byte contents to extract.
   * @return Patch operations or an error message-containing payload for user display.
   */
  def extractPatches(contents: Array[Byte]): Perhaps[Seq[PatchOp]] =
    for {
      // Make sure incoming data is JArray ~ so we wrap single item in a list
      json <- extractJson(contents).flatMap {
        // Make it so that a single patch (outside of a list) also works.
        case single @ JObject(fields) =>
          if (fields.nonEmpty) JArray(List(single)).right
          else PatchValidationError("Patch object can not be empty.").left
        case array @ JArray(items) =>
          if (items.nonEmpty) array.right
          else PatchValidationError("Patch array can not be empty.").left
        case otherwise =>
          PatchValidationError("Unexpected JSON patch type.").left
      }
      _ <- validateJson(json)
      patchOps <- json.arr
        .map { PatchOp.fromJson }
        .sequence[Option, PatchOp]
        .toRightDisjunction(PatchValidationError(write(json)))
    } yield patchOps
}
