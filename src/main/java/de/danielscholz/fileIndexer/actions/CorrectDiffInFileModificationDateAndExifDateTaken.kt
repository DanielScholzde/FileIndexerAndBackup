package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.filterEmptyFiles
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getMediumDescrFullFilePathAndOtherData
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Search for different modification date other than the recording date (info from EXIF image header) und correct it.
 */
class CorrectDiffInFileModificationDateAndExifDateTaken(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(referenceDir: List<MyPath>, ignoreSecondsDiff: Int, ignoreHoursDiff: Int) {

      fun checkModifiedIsDifferent(it: FileLocation): Boolean {
         val imgExifOriginalDate = it.fileContent?.fileMeta?.imgExifOriginalDate ?: return false
         val dateTime1 = it.modified.ignoreMillis().convertToUtcZone()
         val dateTime2 = imgExifOriginalDate.ignoreMillis().convertToUtcZone()
         if (ignoreHoursDiff > 0 || ignoreSecondsDiff > 0) {
            val diff = kotlin.math.abs(dateTime1.toEpochSecond() - dateTime2.toEpochSecond())
            for (hour in 0..ignoreHoursDiff) {
               if (diff in (hour * 60 * 60 - ignoreSecondsDiff)..(hour * 60 * 60 + ignoreSecondsDiff)) {
                  return false
               }
            }
            return true
         }
         return dateTime1 != dateTime2
      }

      val filesReference: Sequence<FileLocation> = pl.loadFileLocationsForPaths(referenceDir, true, false).asSequence().filterEmptyFiles()

      val filtered = filesReference.filter { checkModifiedIsDifferent(it) }

      if (Config.INST.verbose) {
         logger.info("The $referenceDir directory contains the following files with a modification date other than the recording date (info from EXIF header):")
      }

      val count = AtomicInteger()
      filtered
         .sortedBy { fileLocation -> fileLocation.getFullFilePath() }
         .forEach {
            var str = ""
            val imgExifOriginalDate = it.fileContent?.fileMeta?.imgExifOriginalDate
            if (imgExifOriginalDate != null) {
               val file = File(it.getFullFilePath())
               str = if (file.exists()) {
                  val millisec = imgExifOriginalDate.toEpochMilli()
                  if (file.lastModified() != millisec) {
                     if (Config.INST.dryRun || file.setLastModified(millisec)) {
                        " (date/time corrected)"
                     } else {
                        " (modification date could not be changed)"
                     }
                  } else {
                     " (date/time already corrected)"
                  }
               } else {
                  " (File not found)"
               }
            }

            if (Config.INST.verbose) {
               logger.info(it.getMediumDescrFullFilePathAndOtherData() + " " + it.modified.convertToLocalZone().toStr() +
                           " <==> " +
                           it.fileContent?.fileMeta?.imgExifOriginalDate?.convertToLocalZone()?.toStr() + str)
            } else {
               logger.info(it.getFullFilePath() + " " + it.modified.convertToLocalZone().toStr() +
                           " <==> " +
                           it.fileContent?.fileMeta?.imgExifOriginalDate?.convertToLocalZone()?.toStr() + str)
            }
            count.incrementAndGet()
         }

      if (Config.INST.verbose) {
         logger.info("${count.get()} results")
      }
   }

}