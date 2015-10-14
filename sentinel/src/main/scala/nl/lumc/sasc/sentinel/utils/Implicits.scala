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
package nl.lumc.sasc.sentinel.utils

import org.scalatra.servlet.FileItem
import scalaz._

object Implicits {

  /** Implicit class for adding our custom read function to an uploaded file item. */
  implicit class RichFileItem(fi: FileItem) {

    /** Reads the uncompressed contents of the file upload. */
    def readUncompressedBytes(): Array[Byte] =
      nl.lumc.sasc.sentinel.utils.readUncompressedBytes(fi.getInputStream)._1

    /** Reads the contents of the file upload as is. */
    def readBytes(): Array[Byte] = org.scalatra.util.io.readBytes(fi.getInputStream)
  }

  /** Implicit class for adding custom merging function for scalaz's Validation type. */
  implicit class RichValidation[+E, +A](a: Validation[E, A]) {

    // Adapted from scalaz's `+++` method.
    /**
     * Method for merging two Validation objects.
     *
     * If both are Success, then the first one is returned.
     * If one is a Failure, then that one is returned.
     * If both are Failures, then they are concatenated.
     */
    def <@>[EE >: E, AA >: A](b: => Validation[EE, AA])(implicit M2: Semigroup[EE]): Validation[EE, AA] =
      a match {
        case Success(s) => b match {
          case Failure(_) => b
          case Success(_) => a
        }
        case f1 @ Failure(fv1) => b match {
          case Failure(fv2) => Failure(M2.append(fv1, fv2))
          case Success(_)   => f1
        }
      }
  }
}

