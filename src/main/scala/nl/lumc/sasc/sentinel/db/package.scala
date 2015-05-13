package nl.lumc.sasc.sentinel

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._

package object db {

  case class MongodbAccessObject(client: MongoClient, dbName: String) {

    lazy val db: MongoDB = client(dbName)

    lazy val gridfs: GridFS = GridFS(db)
  }

  trait MongodbConnector {

    protected val mongo: MongodbAccessObject
  }
}
