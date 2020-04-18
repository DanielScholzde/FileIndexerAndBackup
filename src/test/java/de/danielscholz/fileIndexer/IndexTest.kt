package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.FileContent
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.IndexRun
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.Queries.filePathRootId
import de.danielscholz.fileIndexer.persistence.common.queryDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals

class IndexTest : BaseTest() {

   private val root = "$baseRootDir/indextest"

   @Test
   fun indexTest() {
      main(arrayOf(Commands.INDEX_FILES.command, "--db", ":memory:", "--headless", "$root/")) { pl ->

         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath where parentFilePath_id <= 10 "))
      }
      // todo asserts
   }

   @Test
   fun insertSpeedTest() {
      fun test(pl: PersistenceLayer) {
         val start = System.currentTimeMillis()
         val indexRun = pl.insertIntoIndexRun(
               IndexRun(0,
                        pl,
                        filePathRootId,
                        "/dsfgdg/",
                        "C:",
                        "",
                        "",
                        null,
                        null,
                        false,
                        Instant.now(),
                        false,
                        false,
                        null,
                        0,
                        0,
                        false))

         val max = 20_000

         for (i in 1..max) {
            val fileContent = pl.insertIntoFileContent(
                  FileContent(0,
                              pl,
                              i.toLong(),
                              "fhslfjasdfkljasdfkljaslfkjasf$i",
                              "lkasdfkjHAFDJHKbfdkjgasdfhkgasdfhgaddfhjgadfjhgadfkjadhfkajsdfhadsdkfjhakfdjhafdhkadf",
                              "ladfjlkafdhgkjsfdghafkghafghafjkghafgjhafdjghffgkjfdhgaaskdjfhaskdjfhhadsfkjhakjfgkh"))

            pl.insertIntoFileLocation(
                  FileLocation(0,
                               pl,
                               fileContent.id,
                               filePathRootId,
                               indexRun.id,
                               "filename$i.txt",
                               "txt",
                               Instant.now(),
                               Instant.now(),
                               false,
                               false,
                               null,
                               indexRun,
                               fileContent))
         }

         println("Inserts/Sek.: " + (max / ((System.currentTimeMillis() - start) / 1000.0)).toInt())
      }

      main(arrayOf(Commands.LIST_INDEX_RUNS.command, "--db", ":memory:")) { pl ->
         transaction(logger, pl.db) {
            test(pl)
         }
      }
   }

   @Test
   fun hashSpeedTest() {
      val data = ByteArray(5_000_000) { _ -> 1 }

      val start = System.currentTimeMillis()
      repeat(30) {
         ChecksumCreator(ByteArrayInputStreamWrapperImpl(data), data.size.toLong(), null, null).calcChecksum()
      }
      println("${(data.size * 30 * 1000L) / (System.currentTimeMillis() - start) / 1_000_000} MB/Sek.")
   }

   @Test
   fun semaphoreSpeedTest() {
      val semaphore = Semaphore(2)
      runBlocking {
         launch(Dispatchers.Unconfined) {
            for (i in 0..100_000) {
               semaphore.acquire()
               semaphore.release()
            }
         }
      }
   }

   @Test
   fun testModifiedDate() {
      var now = LocalDateTime.now()
      if (now.second >= 55) {
         Thread.sleep((61 - now.second) * 1000L)
      }

      createFile("$root/testmodified/a.txt")

      main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "$root/testmodified/")) { pl ->

         assertEquals(1, pl.db.dbQueryUniqueInt("SELECT count(*) FROM FileLocation"))

         val fileLocation1 = pl.getFileLocation(1L)

         now = LocalDateTime.now()
         assertEquals(now.hour, fileLocation1.modified.convertToLocalZone().hour)
         assertEquals(now.minute, fileLocation1.modified.convertToLocalZone().minute)
         assertThat(now.second - fileLocation1.modified.convertToLocalZone().second, lessThanOrEqualTo(2))
      }
   }

}
