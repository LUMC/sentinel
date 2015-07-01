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

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import com.mongodb.gridfs.GridFS.DEFAULT_BUCKET

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
       * @param name pipeline name.
       * @return collection name for the samples parsed from the pipeline's run summary file.
       */
      def pipelineSamples(name: String) = s"$name.samples" // TODO: use enums instead
    }
  }
}
