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
import scala.language.implicitConversions
import scala.reflect.{ ClassTag, ManifestFactory }
import scala.reflect.runtime.{ universe => ru }
import scala.util.{ Failure, Success, Try }

import org.bson.types.ObjectId
import org.json4s._
import org.scalatra.util.io.readBytes

import nl.lumc.sasc.sentinel.models.{ ApiPayload, BaseRunRecord, CaseClass }
import nl.lumc.sasc.sentinel.processors.RunsProcessor

/** General utilities */
package object utils {

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
  def getResourceStream(url: String): Option[InputStream] = Option(getClass.getResourceAsStream(url))

  /** Retrieves a resource as a byte array. */
  def readResourceBytes(url: String) = getResourceStream(url).map(readBytes)

  /**
   * Given a case class type, return the names of its fields.
   *
   * @tparam T Type of case class whose name will be returned.
   * @return Array of case class field names.
   */
  def extractFieldNames[T <: CaseClass](implicit m: Manifest[T]) =
    m.runtimeClass.getDeclaredFields
      .map(_.getName)
      .filter(!_.startsWith("$"))

  /**
   * Returns the MD5 checksum of the string made by concatenating the given input strings.
   *
   * @param seq Input strings.
   * @return MD5 checksum.
   */
  def calcMd5(seq: Seq[String]): String = {
    val digest = MessageDigest.getInstance("MD5")
    seq.foreach { case item => digest.update(item.getBytes) }
    digest.digest().map("%02x".format(_)).mkString
  }

  /**
   * Returns the MD5 checksum of the given byte array.
   *
   * @param arr Byte array.
   * @return MD5 checksum.
   */
  def calcMd5(arr: Array[Byte]): String = {
    // TODO: generalize this with calcMd5(seq: Seq[String])
    val digest = MessageDigest.getInstance("MD5")
    arr.foreach { case item => digest.update(item) }
    digest.digest().map("%02x".format(_)).mkString
  }

  /**
   * Transforms the given input stream into a byte array. If the input stream is gzipped, it will be unzipped first.
   *
   * @param is Input stream.
   * @return A tuple of 2 items: the byte array and a boolean indicating whether the input stream was gzipped or not.
   */
  def readUncompressedBytes(is: InputStream): (Array[Byte], Boolean) = {

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
    val ids = strs.map { str => (tryMakeObjectId(str), str) }
    (ids.collect { case (Success(dbId), _) => dbId },
      ids.collect { case (Failure(_), strId) => strId })
  }

  /** Gets the current UTC time. */
  def utcTimeNow: Date = Date.from(Clock.systemUTC().instant)

  /** Serializer for outgoing JSON representing run documents. */
  val RunDocumentSerializer =
    FieldSerializer[BaseRunRecord]({ case (attr, _) if BaseRunRecord.hiddenAttributes.contains(attr) => None },
      { case field => field })

  /** Serializer for outgoing `ApiPayload` objects. */
  val ApiPayloadSerializer =
    FieldSerializer[ApiPayload]({ case (attr, _) if ApiPayload.hiddenAttributes.contains(attr) => None },
      { case field => field })

  /** JSON format used across the entire package. */
  val SentinelJsonFormats = DefaultFormats + new CustomObjectIdSerializer + RunDocumentSerializer + ApiPayloadSerializer

  object reflect {

    import ru._

    /** Shared mirror instance. */
    private val mirror = ru.runtimeMirror(getClass.getClassLoader)

    /**
     * Given a [[nl.lumc.sasc.sentinel.processors.RunsProcessor]] subclass, returns a single-parameter function that
     * takes a [[nl.lumc.sasc.sentinel.utils.MongodbAccessObject]] instance and instantiates the runs processor. In other
     * words, the function creates a delayed constructor for the runs processor.
     *
     * @tparam T [[nl.lumc.sasc.sentinel.processors.RunsProcessor]] subclass.
     * @return Delayed constructor for the given class.
     */
    def makeDelayedProcessor[T <: RunsProcessor](implicit m: Manifest[T]) = {
      val klass = ru.typeOf[T].typeSymbol.asClass
      val km = mirror.reflectClass(klass)
      val ctor = klass.toType.decl(ru.termNames.CONSTRUCTOR).asMethod
      val ctorm = km.reflectConstructor(ctor)
      (mongo: MongodbAccessObject) => ctorm(mongo).asInstanceOf[RunsProcessor]
    }

    // modified from: http://stackoverflow.com/a/29131875
    /** Method to fetch the type used for storing read statistics in a fragment statistics object dynamically. */
    def getReadStatsManifest[T: TypeTag]: Manifest[T] = {
      val read1Name = "read1"
      val t = typeTag[T]
      val mirror = t.mirror
      def toManifestRec(t: Type): Manifest[_] = {
        val clazz = ClassTag[T](mirror.runtimeClass(t)).runtimeClass.getMethod(read1Name).getReturnType
        if (t.typeArgs.length == 1) {
          val arg = toManifestRec(t.typeArgs.head)
          ManifestFactory.classType(clazz, arg)
        } else if (t.typeArgs.length > 1) {
          val args = t.typeArgs.map(x => toManifestRec(x))
          ManifestFactory.classType(clazz, args.head, args.tail: _*)
        } else {
          ManifestFactory.classType(clazz)
        }
      }
      toManifestRec(t.tpe).asInstanceOf[Manifest[T]]
    }
  }
}
