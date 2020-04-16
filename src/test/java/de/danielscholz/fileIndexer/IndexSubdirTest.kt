package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.persistence.common.queryDebug
import org.junit.Test
import kotlin.test.assertEquals

class IndexSubdirTest : BaseTest() {

   private val root = "$baseRootDir/indextest"

   @Test
   fun test1() {
      createHardlink("$root/testsubindex/a.txt", "$root/testsubindex/subdir1/a.txt")
      createHardlink("$root/testsubindex/a.txt", "$root/testsubindex/subdir2/a.txt")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/testsubindex/subdir1/"))
      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/testsubindex/subdir2/"))

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--updateHardlinksInLastIndex", "$root/testsubindex/")) { pl ->

         assertEquals(5, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))
         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileContent"))

         // todo further asserts
         val fileLocation1 = pl.getFileLocation(1L)
         val fileLocation2 = pl.getFileLocation(2L)
         val fileLocation3 = pl.getFileLocation(3L)

         //assertEquals(1L, fileLocation1.referenceInode)
         //assertEquals(1L, fileLocation2.referenceInode)

         //println(pl.db.queryDebug("SELECT * FROM IndexRun "))
         println(pl.db.queryDebug("SELECT * FROM FileLocation "))
         println(pl.db.queryDebug("SELECT * FROM FileContent "))
         //println(pl.db.queryDebug("SELECT * FROM FilePath "))
      }
   }

}