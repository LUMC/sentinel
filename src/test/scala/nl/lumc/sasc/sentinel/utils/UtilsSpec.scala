package nl.lumc.sasc.sentinel.utils

import org.specs2.mutable.Specification

class UtilsSpec extends Specification {

  "getByteArray" should {

    "be able to parse a nonzipped input stream and return the right flag" in {
      val (arr, unzipped) = getByteArray(getResourceStream("/test.txt"))
      arr must not be empty
      unzipped must beFalse
    }

    "be able to parse a zipped input stream and return the right flag" in {
      val (arr, unzipped) = getByteArray(getResourceStream("/test.txt.gz"))
      arr must not be empty
      unzipped must beTrue
    }

    "return the same array from zipped and unzipped stream with the same unzipped content" in {
      val (arr1, _) = getByteArray(getResourceStream("/test.txt"))
      val (arr2, _) = getByteArray(getResourceStream("/test.txt.gz"))
      arr1 mustEqual arr2
    }
  }

}
