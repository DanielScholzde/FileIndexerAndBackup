package de.danielscholz.fileIndexer.actions

import com.google.common.collect.Multimap
import com.google.common.collect.SetMultimap
import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.util.*

class ListIndexRuns(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(path: MyPath?, details: Boolean) {
      if (path == null) {
         val list: List<IndexRun> = pl.loadAllIndexRun(IndexRunFailures.INCL_FAILURES)

         var map: SetMultimap<IdKey, Long>? = null
         if (details) {
            map = mutableSetMultimapOf()
            list.forEach { indexRun ->
               indexRun.loadFileContents(false).forEach {
                  map!!.put(IdKey(it.id, it), indexRun.id)
               }
            }
         }

         list.map { format(it, map) }.alignColumnsOfAllRows(Regex("@@")).forEach {
            logger.info(it)
         }
         if (list.isEmpty()) {
            logger.info("There are no indexed files")
         }
      } else {
         val list: List<IndexRunFilePathResult> = pl.getPathList(path, false)

         var map: SetMultimap<IdKey, Long>? = null
         if (details) {
            map = mutableSetMultimapOf()
            list.forEach { indexRunFilePathResult ->
               indexRunFilePathResult.loadFileContents(false).forEach {
                  map.put(IdKey(it.id, it), indexRunFilePathResult.indexRun.id)
               }
            }
         }

         list.map { format(it.indexRun, map) }.alignColumnsOfAllRows(Regex("@@")).forEach {
            logger.info(it)
         }
         if (list.isEmpty()) {
            logger.info("The directory $path has no indexed files")
         }
      }
   }

   fun format(indexRun: IndexRun, map: Multimap<IdKey, Long>?): String {
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

      return "No. @@" + indexRun.id + "@@: " +
             indexRun.runDate.convertToLocalZone().toStr() + "  " +
             (if (indexRun.mediumSerial != null) "[" + indexRun.mediumSerial + "]" else "") +
             indexRun.pathPrefix + indexRun.path +
             "  @@" + (if (indexRun.mediumDescription != null) "\"" + indexRun.mediumDescription + "\"" else "") +
             "  @@(files: @@" + fileCount.toStr() + "@@" + str + ")" +
             (if (map != null) "  @@" + getUniqueContentLayerDetails(indexRun.id, map) else "")
   }

   private fun getUniqueContentLayerDetails(indexRunId: Long, map: Multimap<IdKey, Long>): String {
      var size0 = 0L
      var files0 = 0
      var size1 = 0L
      var files1 = 0
      map.asMap().forEach {
         if (it.value.contains(indexRunId)) {
            val size = it.value.size
            if (size == 1) {
               size0 += it.key.fileContent.fileSize
               files0++
            } else if (size == 2) {
               size1 += it.key.fileContent.fileSize
               files1++
            }
         }
      }
      return "only in layers: this: @@${files0.toStr()}@@ (@@${size0.formatAsFileSize().replace(" ", "@@ ")})@@ " +
             "this + one other: @@${files1.toStr()}@@ (@@${size1.formatAsFileSize().replace(" ", "@@ ")})"
   }

   class IdKey(val id: Long, val fileContent: FileContent) {
      override fun equals(other: Any?): Boolean {
         if (this === other) return true
         if (javaClass != other?.javaClass) return false
         other as IdKey
         if (id != other.id) return false
         return true
      }

      override fun hashCode(): Int {
         return id.hashCode()
      }
   }
}