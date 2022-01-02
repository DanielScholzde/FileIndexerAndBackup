package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.common.ifZero
import de.danielscholz.fileIndexer.common.leftPad
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class IndexFilesStats(var currentParallelReads: () -> String) {

   private val logger = LoggerFactory.getLogger(javaClass)

   /** Number of already indexed files (incl. files in archives) */
   var indexedFilesCount = AtomicInteger()

   /** Number of already indexed files (excl. files in archives) */
   var indexedFilesCountNoArchive = AtomicInteger()

   /** total file size of already indexed files (incl. files in archives) */
   var indexedFilesSize = AtomicLong()

   /** total file size of already indexed files (excl. files in archives) */
   var indexedFilesSizeNoArchive = AtomicLong()

   @Volatile
   var currentProcessedFile: String = ""

   /** total file size of all files to index (incl. files in archives) */
   var filesSizeAll = 0L
   var filesCountAll = 0

   @Volatile
   private var timestampMillisBegin = 0L

   private val decimalFormat = DecimalFormat("0.00")

   fun start() {
      timestampMillisBegin = System.currentTimeMillis()
   }

   fun stop() {

   }


   fun getProgressTotal() = "${(indexedFilesSize.get() * 100 / filesSizeAll.ifZero(1))}% " +
         "[${indexedFilesSize.get().formatAsFileSize()} / ${filesSizeAll.formatAsFileSize()}] " +
         "[$indexedFilesCount / $filesCountAll]"

   fun getDbTime() = "" + (Global.stat.queryTime / 1_000_000_000) + " sec. / number: ${Global.stat.queryCount}"


   fun getProcessedMbPerSec() = decimalFormat.format(calcProcessedMbPerSecond()) + " MB/sec."

   private fun calcProcessedMbPerSecond(): Double {
      val processedMbPerSecond = indexedFilesSize.get() /
            ((System.currentTimeMillis() - timestampMillisBegin) / 1_000).ifZero(1) /
            1_000_000.0
//      try {
//         val duration = (System.nanoTime() - startTime) / 1000 // microsec
//         if (duration > 0) {
//            val newVal = (fileSize * 1000 * 1000 / duration) * 1.0
//            if (newVal.isFinite()) {
//               when {
//                  processedBytesPerSecond == 0.0         -> {
//                     processedBytesPerSecond = newVal
//                  }
//                  newVal < processedBytesPerSecond * 0.7 -> {
//                     processedBytesPerSecond = newVal
//                  }
//                  else                                   -> {
//                     processedBytesPerSecond = processedBytesPerSecond * 0.9 + newVal * 0.1
//                  }
//               }
//            }
//         }
//      } catch (e: Exception) {
//         logger.warn("Error on calculating of processedBytesPerSecond")
//      }
      return processedMbPerSecond
   }

   fun getRemainingDuration(): String {
      val processedMbPerSecond = calcProcessedMbPerSecond()
      val secondsLeft = if (processedMbPerSecond > 0.1) ((filesSizeAll - indexedFilesSize.get()) / 1_000_000 / processedMbPerSecond).toLong() else -1
      if (secondsLeft >= 0) {
         return formatTime(secondsLeft)
      }
      return ""
   }

   fun getDuration(): String {
      val duration = (System.currentTimeMillis() - timestampMillisBegin) / 1_000
      return formatTime(duration)
   }

   private fun formatTime(seconds: Long): String {
      fun leftPad(n: Long) = (if (n < 10) "0$n" else "" + n)
      val sec = seconds % 60
      var min = seconds / 60
      val hour = min / 60
      min %= 60
      return "$hour:${leftPad(min)}:${leftPad(sec)}"
   }

   fun getFastModeStats(): String {
      if (Global.stat.fastModeHitCount.get() > 0) {
         return ("" + (Global.stat.fastModeHitCount.get() * 100.0 /
               (Global.stat.fastModeHitCount.get() + Global.stat.notFastModeHitCount.get())).roundToInt()).leftPad(3) + "% Hits"
      }
      return "0% hits"
   }

}