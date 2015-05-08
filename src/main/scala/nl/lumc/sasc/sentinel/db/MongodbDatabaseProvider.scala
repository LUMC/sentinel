package nl.lumc.sasc.sentinel.db

import java.io.{ ByteArrayInputStream, InputStream }
import scala.util.Try

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.{ InputAdapter, OutputAdapter }

trait MongodbDatabaseProvider extends DatabaseProvider { this: InputAdapter with OutputAdapter =>

  def storeRawInput(is: InputStream): Try[DbId] = ???

  def storeReference(ref: Reference): Try[DbId] = ???

  def storeSamples(samples: Seq[BaseSampleDocument]): Try[Seq[DbId]] = ???

  def storeAnnotations(annots: Seq[Annotation]): Try[Seq[DbId]] = ???
}
