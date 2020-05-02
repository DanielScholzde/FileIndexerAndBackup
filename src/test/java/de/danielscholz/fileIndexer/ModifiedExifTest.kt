package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.common.convertToUtcZone
import de.danielscholz.fileIndexer.common.toInstant
import de.danielscholz.fileIndexer.persistence.common.queryDebug
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class ModifiedExifTest : BaseTest() {

   private val root = "$baseRootDir/modifiedExif"

   @Test
   fun test1() {
      copyFile("$root/image.jpg", "$root/subdir/image.jpg")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--progressWindow:no", "--timeZone", "Europe/Berlin", "$root/subdir/")) { pl ->
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileMeta "))
      }

      main(arrayOf(Commands.CORRECT_DIFF_IN_FILE_MODIFICATION_DATE_AND_EXIF_DATE_TAKEN.command, "--db", dbname, "$root/subdir/"))

      val dateTime = File("$root/subdir/image.jpg").lastModified().toInstant().convertToUtcZone()
      assertEquals(2018, dateTime.year)
      assertEquals(7, dateTime.monthValue)
      assertEquals(22, dateTime.dayOfMonth)
      assertEquals(8, dateTime.hour)
      assertEquals(14, dateTime.minute)
      assertEquals(41, dateTime.second)
      assertEquals(0, dateTime.nano)
   }

}