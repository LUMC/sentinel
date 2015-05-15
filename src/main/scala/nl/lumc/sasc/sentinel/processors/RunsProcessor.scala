package nl.lumc.sasc.sentinel.processors

import scala.util.Try

import org.scalatra.servlet.FileItem

import nl.lumc.sasc.sentinel.db._

class RunsProcessor(protected val mongo: MongodbAccessObject) extends RunsAdapter with MongodbConnector {

  def processRun(fi: FileItem, userId: String, pipeline: String) = Try(throw new NotImplementedError)
}
