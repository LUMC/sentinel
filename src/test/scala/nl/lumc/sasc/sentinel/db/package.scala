package nl.lumc.sasc.sentinel

import java.io.InputStream

import scala.util.Success
import nl.lumc.sasc.sentinel.models._
import nl.lumc.sasc.sentinel.processors.RunProcessor

package object db {

  trait AllSuccessDatabaseProvider extends DatabaseProvider { this: RunProcessor =>
    def storeRun(is: InputStream) = Success("")
    def storeReference(ref: Reference) = Success("")
    def storeAnnotations(annots: Seq[Annotation]) = Success(Seq(""))
    def storeSamples(samples: Seq[BaseSampleDocument]) = Success(Seq(""))
    override def process(is: InputStream) = Success(Seq(""))
  }
}
