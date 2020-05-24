package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.CancelPipelineException
import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.fileIndexer.common.ensureSuffix
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MoveFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, basePath: MyPath, toPath: MyPath, deleteEmptyDirs: Boolean) {
      logger.info("Moving files with base path: $basePath")

      val basePathStr = basePath.path.replace("\\", "/").ensureSuffix("/")
      val toDirStr = toPath.path.replace("\\", "/").ensureSuffix("/")

      val matched = mutableListOf<Pair<File, File>>()
      var failures = 0

      files.forEach {
         val fullFilePath = it.getFullFilePath()
         if (fullFilePath.startsWith(basePathStr, true)) {
            val sourceFile = File(fullFilePath)
            if (sourceFile.isFile) {
               matched.add(sourceFile to File(toDirStr + fullFilePath.substring(basePathStr.length).removePrefix("/")))
            } else {
               logger.error("$fullFilePath does not exist or is not a file")
               failures++
            }
         } else {
            logger.error("$fullFilePath does not match to base path $basePath")
            failures++
         }
      }

      if (failures > 0) throw CancelPipelineException()

      var moved = 0
      val pathsWithDeletedFiles = mutableSetOf<File>()

      matched.sortedBy { it.first.path.toLowerCase() }.forEach {
         try {
            if (!Config.INST.dryRun) {
               Files.move(it.first.toPath(), it.second.toPath())
            }
            if (deleteEmptyDirs) {
               it.first.parentFile?.let { pathsWithDeletedFiles.add(it) }
            }
            moved++
         } catch (e: IOException) {
            logger.error("${it.first} could not be moved")
         }
      }

      logger.info("$moved files moved to $toPath")

      var deletedPaths = 0
      if (!Config.INST.dryRun) {
         // the most deepest directories should be deleted at first
         // to achieve this we sort all directories in descending order
         for (dir in pathsWithDeletedFiles.sortedByDescending { it.path }) {
            if (dir.delete()) { // returns true if dir is empty
               deletedPaths++
            }
         }
      }
      if (deletedPaths > 0) {
         logger.info("$deletedPaths empty directories where deleted")
      }
   }

}