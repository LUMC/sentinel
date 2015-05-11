/**
 * Copyright (c) 2015 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.sentinel.processors

import nl.lumc.sasc.sentinel.db._

class AnnotationsProcessor(protected val mongo: MongodbAccessObject) extends AnnotationsAdapter with MongodbConnector
