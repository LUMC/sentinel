package nl.lumc.sasc.sentinel

import java.io.InputStream

import scala.util.Success

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.{ InputAdapter, OutputAdapter }

package object db {

  trait AllSuccessDatabaseProvider extends DatabaseProvider { this: InputAdapter with OutputAdapter =>
    def sampleCollectionName = "samples"
    def storeRawInput(is: InputStream) = Success("")
    def storeReference(ref: Reference) = Success("")
    def storeAnnotations(annots: Seq[Annotation]) = Success(Seq(""))
    def storeSamples(samples: Seq[BaseSampleDocument]) = Success(Seq(""))
    override def storeRun(is: FileItem) = Success(StoreRunResult("", "", Seq(""), Seq("")))
  }
}
