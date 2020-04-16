package de.danielscholz.fileIndexer

import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant

class VerifyTest : BaseTest() {

   private val root = "$baseRootDir/verify"

   @Test
   fun test1() {
      val systemOut = captureSystemOut {
         copyFile("$root/a.txt", "$root/subdir/a.txt")

         main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir")) { pl ->
//         println(pl.db.queryDebug("Select * from FileLocation "))
//         println(pl.db.queryDebug("Select * from FileMeta "))
         }

         main(arrayOf(Commands.VERIFY.command, "--db", dbname, "$root/subdir"))
      }

      assertThat(systemOut, containsString("Keine Differenzen gefunden"))
   }

   @Test
   fun test2() {
      val systemOut = captureSystemOut {
         copyFile("$root/a.txt", "$root/subdir/a.txt")

         main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir")) { pl ->
//         println(pl.db.queryDebug("Select * from FileLocation "))
//         println(pl.db.queryDebug("Select * from FileMeta "))
         }

         File("$root/subdir/a.txt").setLastModified(Instant.now().toEpochMilli() - 10000)

         main(arrayOf(Commands.VERIFY.command, "--db", dbname, "$root/subdir"))
      }

      assertThat(systemOut, containsString("subdir\\a.txt Änderungsdatum hat sich geändert"))
   }

   @Test
   fun test3() {
      val systemOut = captureSystemOut {
         copyFile("$root/a.txt", "$root/subdir/a.txt")

         main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir"))

         Files.write(File("$root/subdir/a.txt").toPath(), ByteArray(0), StandardOpenOption.TRUNCATE_EXISTING)

         main(arrayOf(Commands.VERIFY.command, "--db", dbname, "$root/subdir"))
      }

      assertThat(systemOut, containsString("subdir\\a.txt Dateigröße hat sich geändert"))
   }

   @Test
   fun test4() {
      val systemOut = captureSystemOut {
         copyFile("$root/a.txt", "$root/subdir/a.txt")

         main(arrayOf(Commands.INDEX_FILES.command, "--db", dbname, "--headless", "--timeZone", "Europe/Berlin", "$root/subdir"))

         val file = File("$root/subdir/a.txt")
         val lastModified = file.lastModified()
         Files.write(file.toPath(), ByteArray(file.length().toInt()), StandardOpenOption.TRUNCATE_EXISTING)
         file.setLastModified(lastModified)

         main(arrayOf(Commands.VERIFY.command, "--db", dbname, "$root/subdir"))
      }

      assertThat(systemOut, containsString("subdir\\a.txt Differenz im Inhalt vorhanden"))
   }

}