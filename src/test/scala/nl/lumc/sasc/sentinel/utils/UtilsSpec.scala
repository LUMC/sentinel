package nl.lumc.sasc.sentinel.utils

import java.io.FileInputStream

import org.specs2.mutable.Specification

class UtilsSpec extends Specification {

  "getByteArray" should {

    def fis(url: String) = new FileInputStream(getResourceFile(url))

    "be able to parse a nonzipped input stream and return the right flag" in {
      val (arr, unzipped) = getByteArray(fis("/test.txt"))
      arr must not be empty
      unzipped must beFalse
    }

    "be able to parse a zipped input stream and return the right flag" in {
      val (arr, unzipped) = getByteArray(fis("/test.txt.gz"))
      arr must not be empty
      unzipped must beTrue
    }

    "return the same array from zipped and unzipped stream with the same unzipped content" in {
      val (arr1, _) = getByteArray(fis("/test.txt"))
      val (arr2, _) = getByteArray(fis("/test.txt.gz"))
      arr1 mustEqual arr2
    }
  }

}
