package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.swing.JOptionPane

class DeleteDuplicateFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dirs: List<File>,
           deleteDuplicates: Boolean,
           deletePathFilter: String?,
           inclFilenameOnCompare: Boolean,
           printOnlyDeleted: Boolean) {

      if (dirs.size < 2) {
         logger.error("At least two directories must be specified")
         return
      }
      val paths = mutableListOf<IndexRunFilePathResult>()
      for (dir in dirs.map { dir -> dir.canonicalFile }) {
         val pathResult = pl.getNewestPath("auto", dir, true)
         if (pathResult != null) {
            paths.add(pathResult)
         } else {
            logger.error("The path \"$dir\" could not be found in any file index layer")
         }
      }
      if (paths.size != dirs.size) return

      val hasOnlyPartialHash = paths.filter { it.indexRun.onlyReadFirstMbOfContentForHash != null }.count() > 0
      if (hasOnlyPartialHash) {
         logger.warn("ATTENTION: At least one file index set was created with parameter createHashOnlyForFirstMb!")
      }

      find(paths,
           deleteDuplicates,
           deletePathFilter,
           printOnlyDeleted,
           Intersect(MatchMode.FILE_SIZE +
                     (if (hasOnlyPartialHash) MatchMode.HASH_BEGIN_1MB else MatchMode.HASH) +
                     (if (inclFilenameOnCompare) MatchMode.FILENAME else MatchMode.FILE_SIZE), true),
            // Exclude same FileLocation and Hardlinks as result
           ResultFilter.ID_NEQ and ResultFilter.HARDLINK_NEQ)
   }

   private fun find(paths: List<IndexRunFilePathResult>,
                    deleteDuplicates: Boolean,
                    deletePathFilter: String?,
                    printOnlyDeleted: Boolean,
                    intersect: Intersect,
                    resultFilter: ResultFilter) {
      val foundResult = mutableSetMultimapOf<String, FileLocation>()

      logger.info("Index Layer:")
      paths.forEach {
         logger.info(it.indexRun.runDate.convertToLocalZone().toStr() + " " + it.indexRun.pathPrefix + it.indexRun.path)
      }
      logger.info("")

      for (i in 1..paths.lastIndex) {
         for (findResult2 in intersect.apply(LoadFileLocations(paths[0], pl).load().filterEmptyFiles(),
                                             LoadFileLocations(paths[i], pl).load().filterEmptyFiles()).filter(resultFilter)) {
            val fileLoc1 = findResult2.first
            val fileLoc2 = findResult2.second
            foundResult.put(createKey(fileLoc1, intersect.mode), fileLoc1)
            foundResult.put(createKey(fileLoc2, intersect.mode), fileLoc2)
         }
      }

      var files = 0L
      var size = 0L
      var deletedCount = 0
      var deletedSize = 0L
      var deleteFailedCount = 0

      val pathsWithDeletedFiles = mutableSetOf<File>()
      var deleteConfirmationExists: Boolean? = null

      for (key in foundResult.keySet()) {
         val duplicates = foundResult[key]
         if (duplicates.size < 2) {
            logger.error("ERROR")
            return
         }
         for ((index, it) in duplicates.withIndex()) {
            if (index > 0) {
               files++
               size += it.fileContent!!.fileSize
            }
         }
         for (it in duplicates.map { Triple(it.getFullFilePath(), it.formatOtherData(), it) }.sortedBy { it.first }) {
            var deleted = false
            var deleteFailed = false
            var fileExists = true
            if (deleteDuplicates
                && deletePathFilter.isNotEmpty()
                && !it.third.inArchive
                && it.first.contains(deletePathFilter!!)) {
               try {
                  val file = File(it.first)
                  val length = file.length()
                  if (length == 0L && !file.isFile) {
                     fileExists = false
                     throw IOException() // workaround
                  }
                  if (!Config.INST.dryRun) {
                     if (deleteConfirmationExists == null) {
                        if (!Config.INST.confirmations) {
                           deleteConfirmationExists = true
                        } else {
                           deleteConfirmationExists = JOptionPane.showConfirmDialog(
                                 null,
                                 "All duplicates with the path \"$deletePathFilter\" will be deleted.\n" +
                                 "Do you want to proceed?",
                                 "Confirmation",
                                 JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                        }
                     }
                     if (deleteConfirmationExists) {
                        Files.delete(file.toPath())
                     }
                  }
                  file.parentFile?.let { pathsWithDeletedFiles.add(it) }
                  deleted = true
                  deletedCount++
                  deletedSize += length
               } catch (e: Exception) {
                  deleteFailed = true
                  deleteFailedCount++
               }
            }
            if (!printOnlyDeleted || deleted || deleteFailed) {
               logger.info(it.first + it.second +
                           (if (deleted) " deleted" else "") +
                           (if (deleteFailed) " delete FAILED" +
                                              (if (!fileExists) " (File doesn't exists)" else "") else ""))
            }
         }
         if (!printOnlyDeleted) logger.info("")
      }

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

      logger.info("$files duplicates found (${size.formatAsFileSize()})")
      if (deletedCount > 0) {
         logger.info("$deletedCount duplicates deleted (${deletedSize.formatAsFileSize()})")
      }
      if (deleteFailedCount > 0) {
         logger.warn("$deleteFailedCount duplicates could NOT be deleted")
      }
      if (deletedPaths > 0) {
         logger.info("$deletedPaths empty directories where deleted")
      }
   }
}