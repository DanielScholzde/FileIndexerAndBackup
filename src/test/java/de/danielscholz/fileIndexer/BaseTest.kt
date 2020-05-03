package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.common.syncronizedMutableListOf
import org.junit.After
import org.junit.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.Exception
import java.nio.file.Files
import kotlin.test.fail

open class BaseTest(val dbname: String = "target/test-classes/test.db") {

   protected val logger: Logger = LoggerFactory.getLogger(javaClass)

   protected val baseRootDir = "target/test-classes"

   private val createdFiles = syncronizedMutableListOf<File>()
   private val createdDirs = syncronizedMutableListOf<File>()

   private var outOriginal: PrintStream? = null
   private var outBuffer: ByteArrayOutputStream? = null

   @Before
   fun setUp() {
      init()
   }

   @After
   fun tearDown() {
      clear()
   }

   private fun init() {
      Global.createdFilesCallback = { createdFiles.add(it) }
      Global.createdDirsCallback = { createdDirs.add(it) }

      File(dbname).delete()
   }

   fun setContentOfFile(file: String, content: String? = null) {
      val file1 = File(file)
      if (!createdFiles.contains(file1)) throw Exception("only manually created files should be changed!")
      BufferedWriter(FileWriter(file1)).use {
         it.write(content ?: "")
      }
   }

   fun createFile(file: String, content: String? = null) {
      val file1 = File(file)
      file1.parentFile.myMkdirs()
      file1.delete()
      BufferedWriter(FileWriter(file1)).use {
         it.write(content ?: "")
      }
      createdFiles.add(file1)
   }

   fun createHardlink(source: String, target: String) {
      val file = File(target)
      file.parentFile.myMkdirs()
      file.delete()
      Files.createLink(file.toPath(), File(source).toPath())
      createdFiles.add(file)
   }

   fun copyFile(source: String, target: String) {
      val file = File(target)
      file.parentFile.myMkdirs()
      file.delete()
      Files.copy(File(source).toPath(), file.toPath())
      createdFiles.add(file)
   }

   fun renameFile(existingFile: String, newName: String) {
      val file = File(existingFile)
      val newFile = File(newName)
      if (!file.renameTo(newFile)) throw Exception("rename failed!")
      if (!createdFiles.remove(file)) throw Exception("only manually created files should be renamed!")
      createdFiles.add(newFile)
   }

   fun deleteFile(existingFile: String) {
      val file = File(existingFile)
      if (!file.delete()) throw Exception("delete failed!")
      if (!createdFiles.remove(file)) throw Exception("only manually created files should be deleted!")
   }

   fun captureSystemOut(exec: () -> Unit): String {
      captureSystemOutBegin()
      val result: String
      try {
         exec()
      } finally {
         result = captureSystemOutEnd()
      }
      return result
   }

   fun captureSystemOutBegin() {
      outOriginal = System.out
      outBuffer = ByteArrayOutputStream()
      System.setOut(PrintStream(outBuffer!!, true))
   }

   fun captureSystemOutEnd(): String {
      if (outOriginal != null) {
         System.setOut(outOriginal)
         outOriginal = null
         val str = outBuffer.toString()
         outBuffer!!.reset()
         outBuffer = null
         print(str) // str auch hier ausgeben
         return str
      }
      return ""
   }

   private fun clear() {
      captureSystemOutEnd()

      File(dbname).delete()

      createdFiles.forEach {
         if (it.delete()) logger.debug("$it gelöscht") else
            if (it.exists()) fail("Datei konnte nicht gelöscht werden: $it")
      }
      createdFiles.clear()

      createdDirs.reversed().forEach {
         if (it.delete()) logger.debug("$it gelöscht") else
            if (it.exists()) fail("Verzeichnis konnte nicht gelöscht werden: $it")
      }
      createdDirs.clear()

      Global.createdFilesCallback = {}
      Global.createdDirsCallback = {}
   }

   private fun File.myMkdir(): Boolean {
      if (this.mkdir()) {
         createdDirs.add(this)
         return true
      }
      return false
   }

   private fun File.myMkdirs(): Boolean {
      if (this.exists()) {
         return false
      }
      if (myMkdir()) {
         return true
      }
      val canonFile: File?
      try {
         canonFile = this.canonicalFile
      } catch (e: IOException) {
         return false
      }
      val parent = canonFile!!.parentFile

      return parent != null &&
             (parent.myMkdirs() || parent.exists()) &&
             canonFile.myMkdir()
   }

}