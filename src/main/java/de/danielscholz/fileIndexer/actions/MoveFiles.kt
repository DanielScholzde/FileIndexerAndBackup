package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.ensureSuffix
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MoveFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, basePath: File, toDir: File) {
      logger.info("Moving files with base path: $basePath")

      val basePathStr = basePath.path.replace("\\", "/").ensureSuffix("/")
      val toDirStr = toDir.path.replace("\\", "/").ensureSuffix("/")

      val matched = mutableListOf<Pair<File, File>>()
      var failures = false

      files.forEach {
         val fullFilePath = it.getFullFilePath()
         if (fullFilePath.startsWith(basePathStr, true)) {
            matched.add(File(fullFilePath) to File(toDirStr + fullFilePath.substring(basePathStr.length).removePrefix("/")))
         } else {
            logger.error("$fullFilePath does not match to base path $basePath")
            failures = true
         }
      }

      if (failures) return

      matched.sortedBy { it.first.path.toLowerCase() }.forEach {
         try {
            Files.move(it.first.toPath(), it.second.toPath())
         } catch (e: IOException) {
            logger.error("${it.first} could not be moved")
         }
      }
   }

}