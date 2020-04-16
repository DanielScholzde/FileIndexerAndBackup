package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.common.ifZero
import de.danielscholz.fileIndexer.common.leftPad
import de.danielscholz.fileIndexer.gui.InfopanelSwing
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

class IndexFilesStats(private var currentParallelReads: () -> String) {

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

   /** Number of files in current directory */
   @Volatile
   var filesDir = 0

   /** Number of already processed files in current directory */
   var filesProcessedDir = AtomicInteger()

   @Volatile
   private var timestampMillisBegin = 0L

   @Volatile
   private var processedMbPerSecond: Double = 0.0

   private val decimalFormat = DecimalFormat("0.00")

   private val thread = Thread({ refresh() }, "GUI-Refresh")

   fun startRefresh() {
      timestampMillisBegin = System.currentTimeMillis()
      thread.start()
   }

   fun stopRefresh() {
      thread.interrupt()
   }

   private fun refresh() {
      try {
         while (true) {
            SwingUtilities.invokeLater { updateInfoPanel() }
            Thread.sleep(1000)
         }
      } catch (e: InterruptedException) {
         //
      }
   }

   private fun updateInfoPanel() {
      calcProcessedMbPerSecond()

      InfopanelSwing.setProgressTotal("${(indexedFilesSize.get() * 100 / filesSizeAll.ifZero(1))}% " +
                                      "[${indexedFilesSize.get().formatAsFileSize()} / ${filesSizeAll.formatAsFileSize()}] " +
                                      "[$indexedFilesCount / $filesCountAll]")
      InfopanelSwing.setProgressDirectory("" + (filesProcessedDir.get() * 100 / filesDir.ifZero(1)) + "%")
      InfopanelSwing.setDuration(getDuration())
      InfopanelSwing.setRemainingDuration(getEstimatedDuration())
      InfopanelSwing.setFastModeData(getFastModeStats())
      InfopanelSwing.setNewIndexedData(Global.stat.newIndexedFilesSize.get().formatAsFileSize())
      InfopanelSwing.setProcessedMbPerSec(decimalFormat.format(processedMbPerSecond) + " MB/sec.")
      InfopanelSwing.setDbTime("" + (Global.stat.queryTime / 1_000_000_000) + " sec. / number: ${Global.stat.queryCount}")
      InfopanelSwing.setCurrentParallelReads(currentParallelReads())
      InfopanelSwing.setCurrentProcessedFilename(currentProcessedFile)
   }

   private fun calcProcessedMbPerSecond() {
      processedMbPerSecond = indexedFilesSize.get() /
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
   }

   private fun getEstimatedDuration(): String {
      val secondsLeft = if (processedMbPerSecond > 0.1) ((filesSizeAll - indexedFilesSize.get()) / 1_000_000 / processedMbPerSecond).toLong() else -1
      if (secondsLeft >= 0) {
         return formatTime(secondsLeft)
      }
      return ""
   }

   private fun getDuration(): String {
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

   private fun getFastModeStats(): String {
      if (Global.stat.fastModeHitCount.get() > 0) {
         return ("" + (Global.stat.fastModeHitCount.get() * 100.0 /
                       (Global.stat.fastModeHitCount.get() + Global.stat.notFastModeHitCount.get())).roundToInt()).leftPad(3) + "% Hits"
      }
      return "0% hits"
   }

}