package nl.lumc.sasc.sentinel.processors

import nl.lumc.sasc.sentinel.db._

class ReferencesProcessor(protected val mongo: MongodbAccessObject) extends ReferencesAdapter with MongodbConnector
