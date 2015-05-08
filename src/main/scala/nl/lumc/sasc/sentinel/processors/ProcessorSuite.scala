package nl.lumc.sasc.sentinel.processors

import nl.lumc.sasc.sentinel.db._

trait ProcessorSuite {

  type Db <: DatabaseProvider

  type Output <: OutputAdapter

  type Processor[T <: InputAdapter] = T with OutputAdapter with Db

  def processors: Map[String, Processor[_]]

  def supportedInputVersions: Set[String] = processors.keySet
}
