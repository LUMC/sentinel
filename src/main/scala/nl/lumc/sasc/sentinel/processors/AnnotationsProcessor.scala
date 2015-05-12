package nl.lumc.sasc.sentinel.processors

import nl.lumc.sasc.sentinel.db._

class AnnotationsProcessor(protected val mongo: MongodbAccessObject) extends AnnotationsAdapter with MongodbConnector
