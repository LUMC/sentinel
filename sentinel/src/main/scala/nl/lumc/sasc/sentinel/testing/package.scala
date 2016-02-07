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
/**
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

import org.scalatra.test.{ BytesPart, Uploadable }

import nl.lumc.sasc.sentinel.utils.readResourceBytes

package object testing {

  /** Convenience container for common MIME types. */
  object MimeType {
    val Json = "application/json"
    val Binary = "application/octet-stream"
    val Html = "text/html"
    val Plain = "text/plain"
  }

  trait SentinelTestPart { self: Uploadable =>

    def pipelineName: String

    def toBytesPart: BytesPart = BytesPart(fileName, content)
  }

  /** Upload object for Sentinel pipeline testing. */
  case class PipelinePart(resourceUrl: String, pipelineName: String,
                          nSamples: Int = 0, nReadGroups: Int = 0,
                          contentType: String = "application/octet-stream") extends Uploadable with SentinelTestPart {

    val fileName = resourceUrl.split("/").last

    val content = readResourceBytes(resourceUrl) match {
      case Some(bytes) => bytes
      // Fail fast since we are in testing and we need the resource URL to be present
      case None        => throw new IllegalStateException(s"Required test resource '$resourceUrl' can not be loaded.")
    }

    val contentLength = content.length.toLong
  }

  case class VariableSizedPart(size: Long, pipelineName: String,
                               contentType: String = "application/octet-stream", dummyContent: Byte = 0)
      extends Uploadable with SentinelTestPart {

    val fileName = "inMemory"

    val contentLength = size

    val content = {
      // Workaround since Array.fill only works with Ints but we need Longs
      val b = Array.newBuilder[Byte]
      var i = 0
      while (i < size) {
        b += dummyContent
        i += 1
      }
      b.result()
    }
  }
}
