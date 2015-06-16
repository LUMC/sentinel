package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.Salat
import org.bson.types.ObjectId

/** Representation of a sample within a run. */
@Salat abstract class BaseSampleDocument {

  /** Name of the run summary file uploader. */
  def uploaderId: String

  /** Name of the run which this sample belongs to. */
  def runName: Option[String]

  /** Sample name. */
  def sampleName: Option[String]

  /** Database sample ID. */
  def runId: ObjectId

  /** Libraries belonging to this sample. */
  def libs: Seq[BaseLibDocument]

  /** UTC time when the sample document was created. */
  def creationTimeUtc: Date
}
