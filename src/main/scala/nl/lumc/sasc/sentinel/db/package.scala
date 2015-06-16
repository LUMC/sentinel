package nl.lumc.sasc.sentinel

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import com.mongodb.gridfs.GridFS.DEFAULT_BUCKET

package object db {

  /**
   * MongoDB database access provider.
   *
   * @param client object representing the client connection.
   * @param dbName database name to connect to.
   * @param bucketName GridFS bucket name to connect to.
   */
  case class MongodbAccessObject(client: MongoClient, dbName: String, bucketName: String = DEFAULT_BUCKET) {

    /** MongoDB database. */
    lazy val db: MongoDB = client(dbName)

    /** MongoDB GridFS. */
    def gridfs: GridFS = {
      val gfs = GridFS(db, bucketName)
      db.getCollection(s"$bucketName.files").setWriteConcern(WriteConcern.Acknowledged)
      db.getCollection(s"$bucketName.chunks").setWriteConcern(WriteConcern.Acknowledged)
      gfs
    }
  }

  /** Trait for connecting to a MongoDB database. */
  trait MongodbConnector {

    import MongodbConnector._

    /** Helper container for collection names. */
    final def collectionNames = CollectionNames

    /** MongoDB access. */
    protected def mongo: MongodbAccessObject
  }

  object MongodbConnector {

    /** Default collection names. */
    object CollectionNames {

      /** Annotation records collection name. */
      val Annotations = "annotations"

      /** Reference records collection name. */
      val References = "references"

      /** Run summary files collection name. */
      val Runs = "runs"

      /** User records collection name. */
      val Users = "users"

      /**
       * Retrieves the sample collection name for the given pipeline.
       *
       * @param name pipeline name.
       * @return collection name for the samples parsed from the pipeline's run summary file.
       */
      def pipelineSamples(name: String) = s"$name.samples" // TODO: use enums instead
    }
  }
}
