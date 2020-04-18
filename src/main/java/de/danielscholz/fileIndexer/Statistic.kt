package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.common.syncronizedMutableListOf
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class Statistic(
      var startTime: Long = 0,
      var indexedFilesCount: AtomicInteger = AtomicInteger(),
      var indexedFilesSize: AtomicLong = AtomicLong(),
      var newIndexedFilesCount: AtomicInteger = AtomicInteger(),
      var newIndexedFilesSize: AtomicLong = AtomicLong(),
      var copiedFilesCount: Int = 0,
      var copiedFilesSize: Long = 0,
      var deletedFilesCount: Int = 0,
      var deletedFilesSize: Long = 0,
      var failedDirReads: MutableList<String> = syncronizedMutableListOf(),
      var failedFileReads: MutableList<Pair<File, String?>> = syncronizedMutableListOf(),
      var fastModeHitCount: AtomicInteger = AtomicInteger(),
      var notFastModeHitCount: AtomicInteger = AtomicInteger(),
      var queryCount: AtomicInteger = AtomicInteger(),
      var queryTime: Long = 0,
      var maxQueryTime: AtomicLong = AtomicLong(),
      @Volatile
      var maxQuerySql: String? = null) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun echoStatistics() {
      for (failedDirRead in failedDirReads) {
         logger.info("Directory is not readable: {}", failedDirRead)
      }
      for (failedFileRead in failedFileReads) {
         logger.info("File is not readable: {} ({})", failedFileRead.first, failedFileRead.second)
      }

      if (copiedFilesCount > 0)
         logger.info("Number of copied files: {} ({})", copiedFilesCount, copiedFilesSize.formatAsFileSize())
      if (deletedFilesCount > 0)
         logger.info("Number of deleted files: {} ({})", deletedFilesCount, deletedFilesSize.formatAsFileSize())
      if (indexedFilesCount.get() > 0)
         logger.info("Number of indexed files: {} ({})", indexedFilesCount, indexedFilesSize.get().formatAsFileSize())
      if (newIndexedFilesCount.get() > 0)
         logger.info("Number of NEW indexed files: {} ({})", newIndexedFilesCount, newIndexedFilesSize.get().formatAsFileSize())

      if (fastModeHitCount.get() > 0) {
         logger.info("With FAST_MODE saved db queries: {}",
                     "" + (fastModeHitCount.get() * 100.0 /
                           (fastModeHitCount.get() + notFastModeHitCount.get())).roundToInt()
                     + "% (" + fastModeHitCount.get() + " total)")
      }

      logger.debug("Most time consuming SQL ({} sec):\n{}", maxQueryTime.get() / 100000000 / 10.0, maxQuerySql)
      logger.info("")
   }
}