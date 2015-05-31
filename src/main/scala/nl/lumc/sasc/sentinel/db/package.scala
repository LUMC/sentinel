package nl.lumc.sasc.sentinel

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import com.mongodb.gridfs.GridFS.DEFAULT_BUCKET

package object db {

  case class MongodbAccessObject(client: MongoClient, dbName: String, bucketName: String = DEFAULT_BUCKET) {

    lazy val db: MongoDB = client(dbName)

    def gridfs: GridFS = {
      val gfs = GridFS(db, bucketName)
      db.getCollection(s"$bucketName.files").setWriteConcern(WriteConcern.Acknowledged)
      db.getCollection(s"$bucketName.chunks").setWriteConcern(WriteConcern.Acknowledged)
      gfs
    }
  }

  trait MongodbConnector {

    import MongodbConnector._

    final def collectionNames = CollectionNames

    protected def mongo: MongodbAccessObject
  }

  object MongodbConnector {

    object CollectionNames {
      val Annotations = "annotations"
      val References = "references"
      val Runs = "runs"
      val Users = "users"
      def pipelineSamples(name: String) = s"$name.samples"
    }
  }
}
