package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.persistence.common.queryDebug
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class IndexHardlinksTest : BaseTest() {

   private val root = "$baseRootDir/indexhardlinks"

   @Test
   fun test1() {
      createHardlink("$root/test1/a.txt", "$root/test1/aa.txt")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", ":memory:", "--headless", "$root/test1/")) { pl ->

         assertEquals(2, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))
         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileContent"))

         val fileLocation1 = pl.getFileLocation(1L)
         val fileLocation2 = pl.getFileLocation(2L)

         assertEquals(1L, fileLocation1.referenceInode)
         assertEquals(1L, fileLocation2.referenceInode)

         assertNotNull(fileLocation1.indexRun)
         assertSame(fileLocation1.indexRun, fileLocation2.indexRun)
         assertSame(fileLocation1.fileContent, fileLocation2.fileContent)

         //println(pl.db.queryDebug("SELECT * FROM IndexRun "))
         println(pl.db.queryDebug("SELECT * FROM FileLocation "))
         println(pl.db.queryDebug("SELECT * FROM FileContent "))
         //println(pl.db.queryDebug("SELECT * FROM FilePath "))
      }
   }

   @Test
   fun test2_1() {
      createHardlink("$root/test2/001/a.txt", "$root/test2/002/aa.txt")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/test2/001/"))

      main(arrayOf(Commands.INDEX_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--updateHardlinksInLastIndex",
                   "--lastIndexDir", "$root/test2/001/",
                   "$root/test2/002/")) { pl ->

         assertEquals(2, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))
         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileContent"))

         val fileLocation1 = pl.getFileLocation(1L)
         val fileLocation2 = pl.getFileLocation(2L)

         assertEquals(1L, fileLocation1.referenceInode)
         assertEquals(1L, fileLocation2.referenceInode)

         //println(pl.db.queryDebug("SELECT * FROM IndexRun "))
         println(pl.db.queryDebug("SELECT * FROM FileLocation "))
         println(pl.db.queryDebug("SELECT * FROM FileContent "))
         //println(pl.db.queryDebug("SELECT * FROM FilePath "))
      }
   }

   @Test
   fun test2_2() {
      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/test2/001/"))

      createHardlink("$root/test2/001/a.txt", "$root/test2/002/aa.txt")

      main(arrayOf(Commands.INDEX_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--lastIndexDir", "$root/test2/001/",
                   "$root/test2/002/")) { pl ->

         assertEquals(2, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))
         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileContent"))

         val fileLocation1 = pl.getFileLocation(1L)
         val fileLocation2 = pl.getFileLocation(2L)

         assertNull(fileLocation1.referenceInode)
         assertNull(fileLocation2.referenceInode)

         //println(pl.db.queryDebug("SELECT * FROM IndexRun "))
         println(pl.db.queryDebug("SELECT * FROM FileLocation "))
         println(pl.db.queryDebug("SELECT * FROM FileContent "))
         //println(pl.db.queryDebug("SELECT * FROM FilePath "))
      }
   }

   @Test
   fun test2_3() {
      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/test2/001/"))

      createHardlink("$root/test2/001/a.txt", "$root/test2/002/aa.txt")
      createHardlink("$root/test2/001/a.txt", "$root/test2/002/aaa.txt")

      main(arrayOf(Commands.INDEX_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--lastIndexDir", "$root/test2/001/",
                   "$root/test2/002/")) { pl ->

         assertEquals(3, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))
         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileContent"))

         val fileLocation1 = pl.getFileLocation(1L)
         val fileLocation2 = pl.getFileLocation(2L)
         val fileLocation3 = pl.getFileLocation(3L)

         assertNull(fileLocation1.referenceInode)
         assertEquals(1L, fileLocation2.referenceInode)
         assertEquals(1L, fileLocation3.referenceInode)

         //println(pl.db.queryDebug("SELECT * FROM IndexRun "))
         println(pl.db.queryDebug("SELECT * FROM FileLocation "))
         println(pl.db.queryDebug("SELECT * FROM FileContent "))
         //println(pl.db.queryDebug("SELECT * FROM FilePath "))
      }
   }

}