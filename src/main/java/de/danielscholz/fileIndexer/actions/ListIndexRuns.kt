package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class ListIndexRuns(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dir: File?) {
      if (dir == null) {
         val list = pl.loadAllIndexRun(IndexRunFailures.INCL_FAILURES)
         val maxIdStrLength = list.map { indexRun -> indexRun.id }.max().toString().length
         list.forEach { indexRun ->
            logger.info(format(indexRun, maxIdStrLength))
         }
      } else {
         val list = pl.getPathList(null, dir.canonicalFile)
         val maxIdStrLength = list.map { it.indexRun.id }.max().toString().length
         list.forEach {
            logger.info(format(it.indexRun, maxIdStrLength))
         }
         if (Config.verbose && list.isEmpty()) {
            logger.info("The directory $dir has no indexed files")
         }
      }
   }

   fun format(indexRun: IndexRun, maxIdStrLength: Int): String {
      val description = (indexRun.mediumDescription.isNotEmpty() || indexRun.mediumSerial.isNotEmpty()).ifTrue(
            "  [vsn: " + (indexRun.mediumSerial.ifEmpty("") + " " + indexRun.mediumDescription.ifEmpty("")).trim() + "]", "")

      val fileCount = pl.db.dbQueryUniqueLong(Queries.fileLocation2, listOf(indexRun.id))

      val excludedPaths = ArrayList(indexRun.excludedPaths.split("|").filter { it.isNotEmpty() })
      val excludedFiles = ArrayList(indexRun.excludedFiles.split("|").filter { it.isNotEmpty() })
      excludedPaths.removeAll(Config.defaultExcludedPaths)
      excludedFiles.removeAll(Config.defaultExcludedFiles)

      var str = ""
      str += (excludedPaths.isNotEmpty()).ifTrue(", excl. paths: $excludedPaths", "")
      str += (excludedFiles.isNotEmpty()).ifTrue(", excl. files: $excludedFiles", "")
      str += (indexRun.onlyReadFirstMbOfContentForHash != null).ifTrue(", HashOnlyForFirstMb", "")
      str += (indexRun.failureOccurred).ifTrue(", FAILURE occurred", "")

      return "No. " + indexRun.id.toString().leftPad(maxIdStrLength) + ": " + indexRun.runDate.convertToLocalZone().toStr() + "  " +
             indexRun.pathPrefix + indexRun.path + description + "  (indexed files: " + fileCount.toStr() + str + ")"
   }

}