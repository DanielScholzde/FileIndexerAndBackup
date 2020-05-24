package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File

class FindDuplicateFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(referencePath: MyPath, toSearchInPaths: List<MyPath>, inclFilenameOnCompare: Boolean): List<FileLocation> {

      if (toSearchInPaths.isEmpty()) {
         logger.error("At least one directory to search in must be specified")
         return listOf()
      }

      var pathReference: IndexRunFilePathResult? = null
      val pathRefResult = pl.getNewestPath(referencePath, true)
      if (pathRefResult != null) {
         pathReference = pathRefResult
      } else {
         logger.error("The path \"$referencePath\" could not be found in any file index layer")
      }

      val pathsToSearchIn = mutableListOf<IndexRunFilePathResult>()
      for (path in toSearchInPaths) {
         val pathResult = pl.getNewestPath(path, true)
         if (pathResult != null) {
            pathsToSearchIn.add(pathResult)
         } else {
            logger.error("The path \"$path\" could not be found in any file index layer")
         }
      }
      if (pathsToSearchIn.size != toSearchInPaths.size || pathReference == null) return listOf()

      val hasOnlyPartialHash = pathsToSearchIn.filter { it.indexRun.onlyReadFirstMbOfContentForHash != null }.count() > 0 ||
                               pathReference.indexRun.onlyReadFirstMbOfContentForHash != null
      if (hasOnlyPartialHash) {
         logger.warn("ATTENTION: At least one file index set was created with parameter createHashOnlyForFirstMb!")
      }

      return find(pathReference, pathsToSearchIn,
                  Intersect(MatchMode.FILE_SIZE +
                            (if (hasOnlyPartialHash) MatchMode.HASH_BEGIN_1MB else MatchMode.HASH) +
                            (if (inclFilenameOnCompare) MatchMode.FILENAME else MatchMode.FILE_SIZE), true),
            // Exclude same FileLocation and Hardlinks as result
                  ResultFilter.ID_NEQ and ResultFilter.HARDLINK_NEQ)
   }


   private fun find(referencePath: IndexRunFilePathResult,
                    toSearchInPaths: List<IndexRunFilePathResult>,
                    intersect: Intersect,
                    resultFilter: ResultFilter): List<FileLocation> {

      val foundResult = mutableListMultimapOf<String, FileLocation>()

      logger.info("Index Layer:")
      logger.info(referencePath.indexRun.runDate.convertToLocalZone().toStr() + " " + referencePath.indexRun.pathPrefix + referencePath.indexRun.path)
      toSearchInPaths.forEach {
         logger.info(it.indexRun.runDate.convertToLocalZone().toStr() + " " + it.indexRun.pathPrefix + it.indexRun.path)
      }
      logger.info("")

      val referenceFiles = LoadFileLocations(referencePath, pl).load().filterEmptyFiles()
      for (i in 0..toSearchInPaths.lastIndex) {
         for (findResult2 in intersect.apply(referenceFiles, LoadFileLocations(toSearchInPaths[i], pl).load().filterEmptyFiles()).filter(resultFilter)) {
            val fileLocReference = findResult2.first
            val fileLocToSearchIn = findResult2.second
            val key = createKey(fileLocReference, intersect.mode)
            if (foundResult.get(key).isEmpty()) {
               foundResult.put(key, fileLocReference)
            }
            foundResult.put(key, fileLocToSearchIn)
         }
      }

      var files = 0L
      var size = 0L

      val result = mutableListOf<FileLocation>()

      for (key in foundResult.keySet()) {
         val duplicates: MutableList<FileLocation> = foundResult[key]
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

         val ref = duplicates[0]
         duplicates.removeAt(0)

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

            logger.info(it.first + it.second + (if (!fileExists) " (File doesn't exists)" else "") + " is duplicate to " + ref.getFullFilePath())
         }
         logger.info("")
      }

      logger.info("$files duplicates found (${size.formatAsFileSize()})")

      return result
   }
}