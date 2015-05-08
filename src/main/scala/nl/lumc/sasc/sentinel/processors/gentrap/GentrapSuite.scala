package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.db._
import nl.lumc.sasc.sentinel.processors.ProcessorSuite

object GentrapSuite extends ProcessorSuite {

  type Db = MongodbDatabaseProvider

  type Output = GentrapOutputAdapter

  object GentrapV04Processor extends GentrapV04InputAdapter with Output with Db {
    val sampleCollectionName = "gentrap.samples"
  }

  def processors = Map("v04" -> GentrapV04Processor)
}
