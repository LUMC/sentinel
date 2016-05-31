/*
 * Copyright (c) 2015-2016 Leiden University Medical Center and contributors
 *                         (see AUTHORS.md file for details).
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
package nl.lumc.sasc.sentinel.adapters

import nl.lumc.sasc.sentinel.utils.{FutureMixin, MongodbAccessObject}

/** Trait for connecting to a MongoDB database. */
trait MongodbAdapter {

  import MongodbAdapter._

  /** Helper container for collection names. */
  final def collectionNames = CollectionNames

  /** MongoDB access provider. */
  protected def mongo: MongodbAccessObject
}

object MongodbAdapter {

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
    def pipelineSamples(name: String) = s"$name.samples"

    /**
     * Retrieves the read group collection name for the given pipeline.
     *
     * @param name Pipeline name.
     * @return Collection name for the read groups parsed from the pipeline's run summary file.
     */
    def pipelineReadGroups(name: String) = s"$name.readGroups"
  }
}

/** Trait for MongoDB database connections with Future implicits provided. */
trait FutureMongodbAdapter extends MongodbAdapter with FutureMixin