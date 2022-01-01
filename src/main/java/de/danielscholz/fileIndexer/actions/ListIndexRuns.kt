package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.IndexRun
import de.danielscholz.fileIndexer.persistence.IndexRunFailures
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.Queries
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File

class ListIndexRuns(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dir: File?) {
      if (dir == null) {
         val list = pl.loadAllIndexRun(IndexRunFailures.INCL_FAILURES)

         list.map { format(it) }.transform().forEach {
            logger.info(it)
         }
         if (list.isEmpty()) {
            logger.info("There are no indexed files")
         }
      } else {
         val list = pl.getPathList(null, dir.canonicalFile)

         list.map { format(it.indexRun) }.transform().forEach {
            logger.info(it)
         }
         if (list.isEmpty()) {
            logger.info("The directory $dir has no indexed files")
         }
      }
   }

   fun format(indexRun: IndexRun): String {
      val description = (indexRun.mediumDescription.isNotEmpty() || indexRun.mediumSerial.isNotEmpty()).ifTrue(
         "  [" + (indexRun.mediumSerial.ifEmpty("") + ", " + indexRun.mediumDescription.ifEmpty("")).trim().removeSuffix(",") + "]", ""
      )

      val fileCount = pl.db.dbQueryUniqueLong(Queries.fileLocation2, listOf(indexRun.id))

      val excludedPaths = ArrayList(indexRun.excludedPaths.split("|").filter { it.isNotEmpty() })
      val excludedFiles = ArrayList(indexRun.excludedFiles.split("|").filter { it.isNotEmpty() })
      excludedPaths.removeAll(Config.INST.defaultExcludedPaths)
      excludedFiles.removeAll(Config.INST.defaultExcludedFiles)

      var str = ""
      str += (indexRun.isBackup.ifTrue(", Backup", ""))
      str += (excludedPaths.isNotEmpty()).ifTrue(", excl. paths: $excludedPaths", "")
      str += (excludedFiles.isNotEmpty()).ifTrue(", excl. files: $excludedFiles", "")
      str += (indexRun.onlyReadFirstMbOfContentForHash != null).ifTrue(", HashOnlyForFirstMb", "")
      str += (indexRun.failureOccurred).ifTrue(", FAILURE occurred", "")

      return "No. @@" + indexRun.id + "@@: " + indexRun.runDate.convertToLocalZone().toStr() + "  " +
            indexRun.pathPrefix + indexRun.path + "@@" + description + "  @@(files: " + fileCount.toStr() + str + ")"
   }

   private fun List<String>.transform(): List<String> {
      val listOfLists = this.map { it.split(Regex("@@")) }
      val maxColumnCount = listOfLists.map { it.size }.maxOrNull() ?: 0

      for (i in 0 until maxColumnCount) {
         val max = listOfLists.map { if (i <= it.lastIndex) it[i] else "" }.map { it.length }.maxOrNull() ?: 0
         listOfLists.forEach {
            if (i <= it.lastIndex && it[i].length < max) {
               (it as MutableList<String>)[i] = StringUtils.rightPad(it[i], max)
            }
         }
      }
      return listOfLists.map { StringUtils.join(it, "") }
   }

}