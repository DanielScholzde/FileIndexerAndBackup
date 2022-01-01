package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.convertToLocalZone
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.matching.MatchMode.*
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getMediumDescrFullFilePathAndOtherData
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Searches for different modification date between two or more indexRun layers.
 */
class CorrectDiffInFileModificationDate(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(referenceDir: File, toSearchInDirs: List<File>, ignoreMilliseconds: Boolean) {

      val filesReference: Sequence<FileLocation> = pl.loadFileLocationsForPath("auto", referenceDir, true, false).asSequence().filterEmptyFiles()

      val filesToSearchIn: Sequence<FileLocation> = pl.loadFileLocationsForPaths("auto", toSearchInDirs, true, false).asSequence().filterEmptyFiles()

      val result = filesReference.intersect(filesToSearchIn, HASH + FILE_SIZE + FILENAME, true)
         .filter(if (ignoreMilliseconds) ResultFilter.MODIFIED_SEC_NEQ else ResultFilter.MODIFIED_MILLIS_NEQ)

      if (Config.INST.verbose) {
         logger.info("The reference directory $referenceDir contains the following files with a different modification date than in the other directories:")
      }

      val count = AtomicInteger()
      result
         .sortedBy { fileLocationPair -> fileLocationPair.first.getFullFilePath() }
         .forEach {
            var corrected = false
            if (!Config.INST.dryRun) {
               val file = File(it.second.getFullFilePath())
               val millisec = it.first.modified.toEpochMilli()
               if (file.lastModified() != millisec) {
                  file.setLastModified(millisec)
                  corrected = true
               }
            }

            val str = if (corrected) " (date/time corrected)" else ""

            if (Config.INST.verbose) {
               logger.info(
                  it.first.getMediumDescrFullFilePathAndOtherData() + " " + it.first.modified.convertToLocalZone().toStr() +
                        " <==> " +
                        it.second.getMediumDescrFullFilePathAndOtherData() + it.second.modified.convertToLocalZone().toStr() + str
               )
            } else {
               logger.info(
                  it.first.getFullFilePath() + " " + it.first.modified.convertToLocalZone().toStr() + "" +
                        " <==> " +
                        it.second.getFullFilePath() + it.second.modified.convertToLocalZone().toStr() + str
               )
            }
            count.incrementAndGet()
         }

      if (Config.INST.verbose) {
         logger.info("${count.get()} results")
      }
   }

}