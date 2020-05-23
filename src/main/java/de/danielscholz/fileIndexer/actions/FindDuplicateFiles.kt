package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File

class FindDuplicateFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(referenceDir: File, toSearchInDirs: List<File>, inclFilenameOnCompare: Boolean): List<FileLocation> {

      if (toSearchInDirs.isEmpty()) {
         logger.error("At least one directory to search in must be specified")
         return listOf()
      }

      var pathReference: IndexRunFilePathResult? = null
      val pathRefResult = pl.getNewestPath("auto", referenceDir.canonicalFile, true)
      if (pathRefResult != null) {
         pathReference = pathRefResult
      } else {
         logger.error("The path \"$referenceDir\" could not be found in any file index layer")
      }

      val pathsToSearchIn = mutableListOf<IndexRunFilePathResult>()
      for (dir in toSearchInDirs.map { dir -> dir.canonicalFile }) {
         val pathResult = pl.getNewestPath("auto", dir, true)
         if (pathResult != null) {
            pathsToSearchIn.add(pathResult)
         } else {
            logger.error("The path \"$dir\" could not be found in any file index layer")
         }
      }
      if (pathsToSearchIn.size != toSearchInDirs.size || pathReference == null) return listOf()

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


   private fun find(pathReference: IndexRunFilePathResult,
                    pathsToSearchIn: List<IndexRunFilePathResult>,
                    intersect: Intersect,
                    resultFilter: ResultFilter): List<FileLocation> {

      val foundResult = mutableListMultimapOf<String, FileLocation>()

      logger.info("Index Layer:")
      logger.info(pathReference.indexRun.runDate.convertToLocalZone().toStr() + " " + pathReference.indexRun.pathPrefix + pathReference.indexRun.path)
      pathsToSearchIn.forEach {
         logger.info(it.indexRun.runDate.convertToLocalZone().toStr() + " " + it.indexRun.pathPrefix + it.indexRun.path)
      }
      logger.info("")

      val referenceFiles = LoadFileLocations(pathReference, pl).load().filterEmptyFiles()
      for (i in 0..pathsToSearchIn.lastIndex) {
         for (findResult2 in intersect.apply(referenceFiles, LoadFileLocations(pathsToSearchIn[i], pl).load().filterEmptyFiles()).filter(resultFilter)) {
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