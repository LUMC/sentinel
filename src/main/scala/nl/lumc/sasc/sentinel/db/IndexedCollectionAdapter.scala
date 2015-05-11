package nl.lumc.sasc.sentinel.db

trait IndexedCollectionAdapter { this: MongodbConnector =>

  def createIndices(): Unit = {}

}
