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
package nl.lumc.sasc.sentinel

import java.io.{ InputStream, PushbackInputStream }
import java.util.Date
import java.util.zip.GZIPInputStream
import java.security.MessageDigest
import java.time.Clock
import scala.io.Source
import scala.util.Try

import org.bson.types.ObjectId
import org.json4s._
import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models.RunRecord

/** General utilities */
package object utils {

  import implicits._

  /** Magic byte for all Gzipped files. */
  private val GzipMagic = Seq(0x1f, 0x8b)

  /**
   * Curried function to create a function that calculates percentages.
   *
   * The first argument is the percentage denominator, while the second is the numerator.
   */
  def pctOf: Long => Long => Double = (denom: Long) => (numer: Long) => numer * 100.0 / denom

  /**
   * Retrieves a resource as an input stream.
   *
   * @param url URL of the resource.
   * @return An input stream.
   */
  def getResourceStream(url: String): InputStream = Option(getClass.getResourceAsStream(url)) match {
    case Some(s) => s
    case None    => throw new RuntimeException(s"Resource '$url' can not be found.")
  }

  /** Retrieves a resource as a byte array. */
  def getResourceBytes = getResourceStream _ andThen getByteArray andThen ((res: (Array[Byte], Boolean)) => res._1)

  /**
   * Returns the MD5 checksum of the string made by concatenating the given input strings.
   *
   * @param seq Input strings.
   * @return MD5 checksum.
   */
  def calcSeqMd5(seq: Seq[String]): String = {
    val digest = MessageDigest.getInstance("MD5")
    seq.foreach { case item => digest.update(item.getBytes) }
    digest.digest().map("%02x".format(_)).mkString
  }

  /**
   * Splits a raw URL parameter using the given delimiter.
   *
   * @param param Raw URL parameter string.
   * @param delimiter String delimiter.
   * @param fallback Fallback string.
   * @return String items of the raw URL parameter, or the fallback string.
   */
  def splitParam(param: Option[String], delimiter: String = ",",
                 fallback: Seq[String] = Seq()): Seq[String] = param match {
    case Some(str) => str.split(delimiter).toSeq
    case None      => fallback
  }

  /**
   * Transforms the given input stream into a byte array.
   *
   * @param is Input stream.
   * @return A tuple of 2 items: the byte array and a boolean indicating whether the input stream was gzipped or not.
   */
  def getByteArray(is: InputStream): (Array[Byte], Boolean) = {

    def readAll(i: InputStream) = Source.fromInputStream(i).map(_.toByte).toArray

    val pb = new PushbackInputStream(is, GzipMagic.size)
    val inMagic = Seq(pb.read(), pb.read())
    inMagic.reverse.foreach { pb.unread }

    if (inMagic == GzipMagic) (readAll(new GZIPInputStream(pb)), true)
    else (readAll(pb), false)
  }

  /** Tries to make an Object ID from the given string. */
  def tryMakeObjectId(id: String): Try[ObjectId] = Try(new ObjectId(id))

  /**
   * Given a list of strings, separates it into two subsequences: one where the string is transformed into Object IDs
   * and another where the string can not be transformed into Object IDs.
   *
   * @param strs Input strings.
   * @return A tuple of 2 items: Object IDs and strings that can not be transformed to Object IDs.
   */
  def separateObjectIds(strs: Seq[String]): (Seq[ObjectId], Seq[String]) = {
    val (oids, noids) = strs
      .map { case str => (str.getObjectId, str) }
      .partition { case (x, y) => x.isDefined }
    (oids.map(_._1.get), noids.map(_._2))
  }

  /** Gets the current UTC time. */
  def getUtcTimeNow: Date = Date.from(Clock.systemUTC().instant)

  /** Serializer for outgoing JSON payloads. */
  val RunDocumentSerializer = FieldSerializer[RunRecord](FieldSerializer.ignore("sampleIds"), { case field => field })

  /** JSON format used across the entire package. */
  val SentinelJsonFormats = DefaultFormats + new CustomObjectIdSerializer + RunDocumentSerializer

  object implicits {

    import scala.language.{ higherKinds, implicitConversions }

    /** Implicit class for adding our custom read function to an uploaded file item. */
    implicit class RichFileItem(fi: FileItem) {
      def readInputStream(): (Array[Byte], Boolean) = getByteArray(fi.getInputStream)
    }

    /** Implicit class for creating database IDs from raw strings. */
    implicit class DatabaseId(id: String) {
      def getObjectId: Option[ObjectId] = tryMakeObjectId(id).toOption
    }

    /** Implicit class for creating enum values from raw strings. */
    implicit class EnumableString(raw: String) {
      def asEnum[T <: Enumeration#Value](enm: Map[String, T]): Option[T] = enm.get(raw)
    }
  }
}
