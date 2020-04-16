package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.matching.MatchMode.FILE_SIZE
import de.danielscholz.fileIndexer.matching.MatchMode.HASH
import de.danielscholz.fileIndexer.matching.filterEmptyFiles
import de.danielscholz.fileIndexer.matching.plus
import de.danielscholz.fileIndexer.matching.subtract
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getMediumDescrFullFilePathAndOtherData
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class FindFilesWithNoCopy(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(referenceDir: File, toSearchInDirs: List<File>, reverse: Boolean) {

      val filesReference: Sequence<FileLocation> = pl.loadFileLocationsForPath("auto", referenceDir, true, true).asSequence().filterEmptyFiles()

      val filesToSearchIn: Sequence<FileLocation> = pl.loadFileLocationsForPaths("auto", toSearchInDirs, true, true).asSequence().filterEmptyFiles()

      val result = if (!reverse) {
         filesReference.subtract(filesToSearchIn, HASH + FILE_SIZE, true)
      } else {
         filesToSearchIn.subtract(filesReference, HASH + FILE_SIZE, true)
      }

      if (Config.verbose) {
         if (reverse) {
            logger.info("In the directory $referenceDir there are no copies of the following files from the other directories:")
         } else {
            logger.info("The directory $referenceDir contains the following files, which have no copy in the other directories:")
         }
      }

      val count = AtomicInteger()
      result
         .sortedBy { fileLocation -> fileLocation.getFullFilePath() }
         .forEach {
            if (Config.verbose) {
               logger.info(it.getMediumDescrFullFilePathAndOtherData())
            } else {
               logger.info(it.getFullFilePath())
            }
            count.incrementAndGet()
         }

      if (Config.verbose) {
         logger.info("${count.get()} results")
      }
   }

}