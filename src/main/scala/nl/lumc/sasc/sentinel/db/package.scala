/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel

import scala.util.Try

import com.mongodb.{ MongoCredential, ServerAddress }
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import com.mongodb.gridfs.GridFS.DEFAULT_BUCKET
import com.typesafe.config.ConfigFactory

import nl.lumc.sasc.sentinel.settings._

package object db {

  /**
   * MongoDB database access provider.
   *
   * This object encapsulates database access for our internally-defined adapters. It is meant to be instantiated
   * during site initialization and then passed down to each controllers which use adapters for database access.
   *
   * @param client Object representing the client connection.
   * @param dbName Database name to connect to.
   * @param bucketName GridFS bucket name to connect to. If not supplied, is set to `fs`, which is the default MongoDB
   *                   bucket name.
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

  object MongodbAccessObject {

    /** Helper method to create the object using the underlying Java Client object. */
    def fromJava(underlying: com.mongodb.MongoClient, dbName: String, bucketName: String = DEFAULT_BUCKET) =
      MongodbAccessObject(new MongoClient(underlying), dbName, bucketName)

    /** Helper method to create the object using default settings. */
    def withDefaultSettings = {

      val conf = ConfigFactory.load()

      /** Database server hostname. */
      val host = Try(conf.getString(s"$DbConfKey.host")).getOrElse("localhost")

      /** Database server port. */
      val port = Try(conf.getInt(s"$DbConfKey.port")).getOrElse(27017)

      /** Database name. */
      val dbName = Try(conf.getString(s"$DbConfKey.dbName")).getOrElse("sentinel")

      /** Username for database server. */
      val userName = Try(conf.getString(s"$DbConfKey.userName")).toOption

      /** Password for database authentication. */
      val password = Try(conf.getString(s"$DbConfKey.password")).toOption

      // Create authenticated connection only when both userName and password are supplied
      val addr = new ServerAddress(host, port)
      val client = (userName, password) match {
        case (Some(usr), Some(pwd)) =>
          val cred = MongoCredential.createScramSha1Credential(usr, dbName, pwd.toCharArray)
          MongoClient(addr, List(cred))
        case otherwise => MongoClient(addr)
      }

      MongodbAccessObject(client, dbName)
    }
  }

  /** Trait for connecting to a MongoDB database, meant to be mixed into adapters which require database access. */
  trait MongodbConnector {

    import MongodbConnector._

    /** Helper container for collection names. */
    final def collectionNames = CollectionNames

    /** MongoDB access provider. */
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
       * @param name Pipeline name.
       * @return Collection name for the samples parsed from the pipeline's run summary file.
       */
      def pipelineSamples(name: String) = s"$name.samples" // TODO: use enums instead

      /**
       * Retrieves the library collection name for the given pipeline.
       *
       * @param name Pipeline name.
       * @return Collection name for the libraries parsed from the pipeline's run summary file.
       */
      def pipelineLibs(name: String) = s"$name.libs"
    }
  }
}
