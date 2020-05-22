package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File

class FindDuplicateFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dirs: List<File>, inclFilenameOnCompare: Boolean): List<FileLocation> {

      if (dirs.size < 2) {
         logger.error("At least two directories must be specified")
         return listOf()
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
      if (paths.size != dirs.size) return listOf()

      val hasOnlyPartialHash = paths.filter { it.indexRun.onlyReadFirstMbOfContentForHash != null }.count() > 0
      if (hasOnlyPartialHash) {
         logger.warn("ATTENTION: At least one file index set was created with parameter createHashOnlyForFirstMb!")
      }

      return find(paths,
                  Intersect(MatchMode.FILE_SIZE +
                            (if (hasOnlyPartialHash) MatchMode.HASH_BEGIN_1MB else MatchMode.HASH) +
                            (if (inclFilenameOnCompare) MatchMode.FILENAME else MatchMode.FILE_SIZE), true),
            // Exclude same FileLocation and Hardlinks as result
                  ResultFilter.ID_NEQ and ResultFilter.HARDLINK_NEQ)
   }


   private fun find(paths: List<IndexRunFilePathResult>,
                    intersect: Intersect,
                    resultFilter: ResultFilter): List<FileLocation> {
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

      val result = mutableListOf<FileLocation>()

      for (key in foundResult.keySet()) {
         val duplicates = foundResult[key]
         if (duplicates.size < 2) {
            logger.error("ERROR")
            return listOf()
         }

         for ((index, it) in duplicates.withIndex()) {
            if (index > 0) {
               files++
               size += it.fileContent!!.fileSize
            }
         }

         for (it in duplicates.map { Triple(it.getFullFilePath(), it.formatOtherData(), it) }.sortedBy { it.first }) {
            var fileExists = true
            if (!it.third.inArchive) {
               val file = File(it.first)
               val length = file.length()
               if (length == 0L && !file.isFile) {
                  fileExists = false
               } else {
                  result.add(it.third)
               }
            }

            logger.info(it.first + it.second + (if (!fileExists) " (File doesn't exists)" else ""))
         }
         logger.info("")
      }

      logger.info("$files duplicates found (${size.formatAsFileSize()})")

      return result
   }
}