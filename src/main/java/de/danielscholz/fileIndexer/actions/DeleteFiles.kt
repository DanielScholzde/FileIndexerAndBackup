package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.common.formatOtherData
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.swing.JOptionPane

class DeleteFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, deleteEmptyDirs: Boolean) {
      var deletedCount = 0
      var deletedSize = 0L
      var deleteFailedCount = 0

      val pathsWithDeletedFiles = mutableSetOf<File>()
      var deleteConfirmationExists: Boolean? = null

      for (it in files.map { Triple(it.getFullFilePath(), it.formatOtherData(), it) }.sortedBy { it.first }) {
         var deleted = false
         var deleteFailed = false
         var fileExists = true
         if (!it.third.inArchive) {
            try {
               val file = File(it.first)
               val length = file.length()
               if (length == 0L && !file.isFile) {
                  fileExists = false
                  throw IOException() // workaround
               }

               if (!Config.INST.dryRun) {
                  if (deleteConfirmationExists == null) {
                     deleteConfirmationExists = if (!Config.INST.confirmations) {
                        true
                     } else {
                        JOptionPane.showConfirmDialog(
                              null,
                              "All ${files.size} files will be deleted.\n" +
                              "Do you want to proceed?",
                              "Confirmation",
                              JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                     }
                  }
                  if (deleteConfirmationExists) {
                     Files.delete(file.toPath())
                  }
               }

               if (deleteEmptyDirs) {
                  file.parentFile?.let { pathsWithDeletedFiles.add(it) }
               }
               deleted = true
               deletedCount++
               deletedSize += length
            } catch (e: Exception) {
               deleteFailed = true
               deleteFailedCount++
            }
         }

         logger.info(it.first + it.second +
                     (if (deleted) " deleted" else "") +
                     (if (deleteFailed) " delete FAILED" + (if (!fileExists) " (File doesn't exists)" else "") else ""))
      }
      logger.info("")

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

      if (deletedCount > 0) {
         logger.info("$deletedCount files deleted (${deletedSize.formatAsFileSize()})")
      }
      if (deleteFailedCount > 0) {
         logger.warn("$deleteFailedCount files could NOT be deleted")
      }
      if (deletedPaths > 0) {
         logger.info("$deletedPaths empty directories where deleted")
      }
   }

}