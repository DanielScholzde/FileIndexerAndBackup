package de.danielscholz.fileIndexer

import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test

class ShowHelpTest : BaseTest() {

   @Test
   fun test1() {
      val systemOut = captureSystemOut {
         main(arrayOf("help"))
      }
      Assert.assertThat(systemOut, Matchers.containsString("All supported parameters are:"))
   }

   @Test
   fun test2() {
      val systemOut = captureSystemOut {
         main(arrayOf("index", "?"))
      }
      Assert.assertThat(systemOut, Matchers.containsString("All supported parameters are:"))
   }

   @Test
   fun testFallback1() {
      val systemOut = captureSystemOut {
         main(arrayOf("/?"))
      }
      Assert.assertThat(systemOut, Matchers.containsString("All supported parameters are:"))
      // todo further asserts
   }
}