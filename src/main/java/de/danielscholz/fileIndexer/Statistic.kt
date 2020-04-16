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
         logger.info("Verzeichnis nicht lesbar: {}", failedDirRead)
      }
      for (failedFileRead in failedFileReads) {
         logger.info("Datei nicht lesbar: {} ({})", failedFileRead.first, failedFileRead.second)
      }

      if (copiedFilesCount > 0)
         logger.info("Anzahl der kopierten Dateien: {} (Größe: {})", copiedFilesCount, copiedFilesSize.formatAsFileSize())
      if (deletedFilesCount > 0)
         logger.info("Anzahl der gelöschten Dateien: {} (Größe: {})", deletedFilesCount, deletedFilesSize.formatAsFileSize())
      if (indexedFilesCount.get() > 0)
         logger.info("Anzahl der indexierten Dateien: {} (Größe: {})", indexedFilesCount, indexedFilesSize.get().formatAsFileSize())
      if (newIndexedFilesCount.get() > 0)
         logger.info("Anzahl der NEU in den Index aufgenommenen Dateien: {} (Größe: {})",
                     newIndexedFilesCount,
                     newIndexedFilesSize.get().formatAsFileSize())

      if (fastModeHitCount.get() > 0) {
         logger.info("Durch FAST_MODE eingesparte DB-Abfragen: {}",
                     "" + (fastModeHitCount.get() * 100.0 /
                           (fastModeHitCount.get() + notFastModeHitCount.get())).roundToInt()
                     + "% (" + fastModeHitCount.get() + " insgesamt)")
      }

      logger.debug("Langsamstes SQL ({} s):\n{}", maxQueryTime.get() / 100000000 / 10.0, maxQuerySql)
      logger.info("")
   }
}