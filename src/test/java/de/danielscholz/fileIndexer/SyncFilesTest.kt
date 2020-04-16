package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.persistence.common.queryDebug
import org.junit.Test
import java.io.File
import java.nio.file.Files

// todo asserts
class SyncFilesTest : BaseTest() {

   private val root = "$baseRootDir/sync"

   @Test
   fun test1() {
      Files.createDirectories(File("$root/target").toPath())

      main(arrayOf(Commands.SYNC_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--silent",
                   "--mediumSerialSource", "S",
                   "--mediumSerialTarget", "T",
                   "--timeZone", "Europe/Berlin",
                   "$root/source/", "$root/target/")) { pl ->
         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath "))
      }
   }

   @Test
   fun test2() {
      Files.createDirectories(File("$root/target").toPath())
      waitUntilNextSecond()

      createFile("$root/source/file1.txt", "content1")
      createFile("$root/source/file2.txt", "content2")
      copyFile("$root/source/file2.txt", "$root/source/file2Copy.txt")
      createFile("$root/source/file3.txt", "content3")

      main(arrayOf(Commands.SYNC_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--silent",
                   "--timeZone", "Europe/Berlin",
                   "--mediumSerialSource", "S",
                   "--mediumSerialTarget", "T",
                   "$root/source/", "$root/target/"))

      waitUntilNextSecond()

      setContentOfFile("$root/source/file1.txt", "content11")
      renameFile("$root/source/file2.txt", "$root/source/subdir/file2_.txt")
      deleteFile("$root/source/file3.txt")

      main(arrayOf(Commands.SYNC_FILES.command,
                   "--db", dbname,
                   "--headless",
                   "--silent",
                   "--timeZone", "Europe/Berlin",
                   "--mediumSerialSource", "S",
                   "--mediumSerialTarget", "T",
                   "$root/source/", "$root/target/")) { pl ->
         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath "))
      }

   }

   private fun waitUntilNextSecond() = Thread.sleep((1000 - (System.currentTimeMillis() % 1000)) + 10)
}