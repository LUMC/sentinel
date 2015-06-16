package nl.lumc.sasc.sentinel.models

import java.util.Date

import com.novus.salat.annotations.{ Key, Salat }
import org.bson.types.ObjectId

/** Representation of an uploaded run summary file. */
@Salat abstract class BaseRunRecord {

  /** Run name. */
  def name: Option[String]

  /** Database run ID. */
  def runId: ObjectId

  /** Run uploader ID. */
  def uploaderId: String

  /** Name of the pipeline that produced the run. */
  def pipeline: String

  /** Number of samples in the run summary file used for statistics. */
  def nSamples: Int

  /** Number of libraries in the run summary file used for statistics. */
  def nLibs: Int

  /** UTC time when the run record was created. */
  def creationTimeUtc: Date

  /** UTC time when the run record was deleted. */
  def deletionTimeUtc: Option[Date]
}

/**
 * Simple implementation of a run record.
 *
 * @param sampleIds Database sample document IDs contained in the sample.
 * @param refId Reference record ID contained in the sample.
 * @param annotIds Annotation record IDs contained in the sample.
 */
case class RunRecord(
  @Key("_id") runId: ObjectId,
  uploaderId: String,
  pipeline: String,
  nSamples: Int,
  nLibs: Int,
  creationTimeUtc: Date,
  sampleIds: Seq[ObjectId] = Seq(),
  refId: Option[ObjectId] = None,
  annotIds: Option[Seq[ObjectId]] = None,
  name: Option[String] = None,
  deletionTimeUtc: Option[Date] = None) extends BaseRunRecord
