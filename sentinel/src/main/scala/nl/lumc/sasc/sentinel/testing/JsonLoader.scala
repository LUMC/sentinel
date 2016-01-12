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
package nl.lumc.sasc.sentinel.testing

import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import nl.lumc.sasc.sentinel.utils.getResourceStream

trait JsonLoader {
  /** Given a schema URL, parses the file as JSON and returns its contents as a JValue object. */
  def loadJson(url: String): JValue = getResourceStream(url) match {
    // fail immediately since we are in testing and we need the resource anyway
    case None         => throw new IllegalStateException(s"Required test resource '$url' can not be loaded.")
    case Some(stream) => parse(stream)
  }
}
