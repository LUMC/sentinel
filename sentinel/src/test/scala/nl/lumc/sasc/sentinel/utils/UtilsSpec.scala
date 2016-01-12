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

import org.specs2.mutable.Specification

class UtilsSpec extends Specification {

  "getByteArray" should {

    "be able to parse a nonzipped input stream and return the right flag" in {
      getResourceStream("/test.txt") must beSome.like {
        case stream =>
          val (arr, unzipped) = readUncompressedBytes(stream)
          arr must not be empty
          unzipped must beFalse
      }
    }

    "be able to parse a zipped input stream and return the right flag" in {
      getResourceStream("/test.txt.gz") must beSome.like {
        case stream =>
          val (arr, unzipped) = readUncompressedBytes(stream)
          arr must not be empty
          unzipped must beTrue
      }
    }

    "return the same array from zipped and unzipped stream with the same unzipped content" in {
      val arr1 = getResourceStream("/test.txt").map(readUncompressedBytes).map(_._1)
      val arr2 = getResourceStream("/test.txt.gz").map(readUncompressedBytes).map(_._1)
      arr1 must beSome
      arr2 must beSome
      arr1.get mustEqual arr2.get
    }
  }

}
