package de.danielscholz.fileIndexer

import org.hamcrest.Matchers.greaterThan
import org.junit.Assert.assertThat
import org.junit.Test
import kotlin.test.assertEquals

class RemoveIndexTest : BaseTest() {

   private val root = "$baseRootDir/removeIndex"

   @Test
   fun test1() {
      copyFile("$root/image.jpg", "$root/subdir/image.jpg")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir/")) { pl ->
         val i1 = pl.db.dbQueryUniqueInt("Select count(*) from FileLocation ")
         val i2 = pl.db.dbQueryUniqueInt("Select count(*) from FileContent ")
         val i3 = pl.db.dbQueryUniqueInt("Select count(*) from FileMeta ")
         val i4 = pl.db.dbQueryUniqueInt("Select count(*) from IndexRun ")
         val i5 = pl.db.dbQueryUniqueInt("Select count(*) from FilePath ")

         assertEquals(1, i1)
         assertEquals(1, i2)
         assertEquals(1, i3)
         assertEquals(1, i4)
         assertThat(i5, greaterThan(1))
      }

      main(arrayOf(Commands.REMOVE_INDEX.command, "--db", dbname, "1")) { pl ->
         val i1 = pl.db.dbQueryUniqueInt("Select count(*) from FileLocation ")
         val i2 = pl.db.dbQueryUniqueInt("Select count(*) from FileContent ")
         val i3 = pl.db.dbQueryUniqueInt("Select count(*) from FileMeta ")
         val i4 = pl.db.dbQueryUniqueInt("Select count(*) from IndexRun ")
         val i5 = pl.db.dbQueryUniqueInt("Select count(*) from FilePath ")

         assertEquals(0, i1)
         assertEquals(0, i2)
         assertEquals(0, i3)
         assertEquals(0, i4)
         assertEquals(0, i5)
      }
   }

   @Test
   fun test2() {
      copyFile("$root/image.jpg", "$root/subdir/image1.jpg")

      var filePathCount = 0

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir/")) { pl ->
         filePathCount = pl.db.dbQueryUniqueInt("Select count(*) from FilePath ")
      }

      copyFile("$root/image.jpg", "$root/subdir/subdir2/image2.jpg")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir/")) { pl ->
         val i1 = pl.db.dbQueryUniqueInt("Select count(*) from FileLocation ")
         val i2 = pl.db.dbQueryUniqueInt("Select count(*) from FileContent ")
         val i3 = pl.db.dbQueryUniqueInt("Select count(*) from FileMeta ")
         val i4 = pl.db.dbQueryUniqueInt("Select count(*) from IndexRun ")
         val i5 = pl.db.dbQueryUniqueInt("Select count(*) from FilePath ")

         assertEquals(3, i1)
         assertEquals(1, i2)
         assertEquals(1, i3)
         assertEquals(2, i4)
         assertEquals(filePathCount + 1, i5)
      }

      main(arrayOf(Commands.REMOVE_INDEX.command, "--db", dbname, "2")) { pl ->
         val i1 = pl.db.dbQueryUniqueInt("Select count(*) from FileLocation ")
         val i2 = pl.db.dbQueryUniqueInt("Select count(*) from FileContent ")
         val i3 = pl.db.dbQueryUniqueInt("Select count(*) from FileMeta ")
         val i4 = pl.db.dbQueryUniqueInt("Select count(*) from IndexRun ")
         val i5 = pl.db.dbQueryUniqueInt("Select count(*) from FilePath ")

         assertEquals(1, i1)
         assertEquals(1, i2)
         assertEquals(1, i3)
         assertEquals(1, i4)
         assertEquals(filePathCount, i5)
      }
   }
}