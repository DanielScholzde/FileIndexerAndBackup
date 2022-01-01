package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.persistence.common.queryDebug
import org.junit.Test
import java.io.File
import java.nio.file.Files

// todo asserts
class BackupFilesTest : BaseTest() {

   private val root = "$baseRootDir/backup"

   @Test
   fun test1() {
      Files.createDirectories(File("$root/target").toPath())

      main(
         arrayOf(
            Commands.BACKUP_FILES.command,
            "--db", dbname,
            "--progressWindow:no",
            "--confirmations:no",
            "--mediumSerialSource", "S",
            "--mediumSerialTarget", "T",
            "--timeZone", "Europe/Berlin",
            "$root/source/", "$root/target/"
         )
      ) { pl ->
         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath "))
      }

      main(
         arrayOf(
            Commands.LIST_INDEX_RUNS.command,
            "--db", dbname,
            "$root/target/"
         )
      )
   }

   @Test
   fun test2() {
      Files.createDirectories(File("$root/target").toPath())
      waitUntilNextSecond()

      main(
         arrayOf(
            Commands.BACKUP_FILES.command,
            "--db", dbname,
            "--progressWindow:no",
            "--confirmations:no",
            "--timeZone", "Europe/Berlin",
            "--mediumSerialSource", "S",
            "--mediumSerialTarget", "T",
            "$root/source/", "$root/target/"
         )
      )

      waitUntilNextSecond()

      main(
         arrayOf(
            Commands.BACKUP_FILES.command,
            "--db", dbname,
            "--progressWindow:no",
            "--confirmations:no",
            "--timeZone", "Europe/Berlin",
            "--mediumSerialSource", "S",
            "--mediumSerialTarget", "T",
            "$root/source/", "$root/target/"
         )
      ) { pl ->
         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath "))
      }

   }

   @Test
   fun test3() {
      Files.createDirectories(File("$root/target").toPath())
      waitUntilNextSecond()

      createFile("$root/source/file1.txt", "content1")
      createFile("$root/source/file2.txt", "content2")
      copyFile("$root/source/file2.txt", "$root/source/file2Copy.txt")
      createFile("$root/source/file3.txt", "content3")

      main(
         arrayOf(
            Commands.BACKUP_FILES.command,
            "--db", dbname,
            "--progressWindow:no",
            "--confirmations:no",
            "--timeZone", "Europe/Berlin",
            "--mediumSerialSource", "S",
            "--mediumSerialTarget", "T",
            "$root/source/", "$root/target/"
         )
      )

      waitUntilNextSecond()

      setContentOfFile("$root/source/file1.txt", "content11")
      renameFile("$root/source/file2.txt", "$root/source/subdir/file2_.txt")
      deleteFile("$root/source/file3.txt")

      main(
         arrayOf(
            Commands.BACKUP_FILES.command,
            "--db", dbname,
            "--progressWindow:no",
            "--confirmations:no",
            "--timeZone", "Europe/Berlin",
            "--mediumSerialSource", "S",
            "--mediumSerialTarget", "T",
            "$root/source/", "$root/target/"
         )
      ) { pl ->
         println(pl.db.queryDebug("Select * from IndexRun "))
         println(pl.db.queryDebug("Select * from FileLocation "))
         println(pl.db.queryDebug("Select * from FileContent "))
         println(pl.db.queryDebug("Select * from FilePath "))
      }

   }

   private fun waitUntilNextSecond() = Thread.sleep((1000 - (System.currentTimeMillis() % 1000)) + 10)
}