package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.convertToLocalZone
import de.danielscholz.fileIndexer.common.ignoreMillis
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.common.toStrSys
import de.danielscholz.fileIndexer.matching.filterEmptyFiles
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getMediumDescrFullFilePathAndOtherData
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 */
class RenameFilesToModificationDate(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dirs: List<File>) {

      val filesReference: Sequence<FileLocation> = pl.loadFileLocationsForPaths("auto", dirs, true, false).asSequence().filterEmptyFiles()

      val filtered = filesReference.filter {
         val createFilename = createFilename(it)
         it.fileContent?.fileMeta?.imgExifOriginalDate != null && it.filename != createFilename && !it.filename.contains(createFilename)
      }

      if (Config.INST.verbose) {
         logger.info("The directory $dirs contains the following files with a different file name than the modification date:")
      }

      val count = AtomicInteger()
      filtered
         .sortedBy { fileLocation -> fileLocation.getFullFilePath() }
         .forEach {
            var newFilename = createFilename(it)
            var str = ""
            val file = File(it.getFullFilePath())
            if (file.exists()) {
               if (file.lastModified() == it.modified.toEpochMilli()) {
                  val createNotExistingFile = createNotExistingFile(file, newFilename)
                  newFilename = createNotExistingFile.name
                  if (Config.INST.dryRun || file.renameTo(createNotExistingFile)) {
                     str = " (filename changed)"
                  } else {
                     str = " (filename could not be changed)"
                  }
               } else {
                  str = " (file modification date has changed. please create a new index)"
               }
            } else {
               str = " (file not found)"
            }

            if (Config.INST.verbose) {
               logger.info(it.getMediumDescrFullFilePathAndOtherData() + " " + it.modified.convertToLocalZone().toStr() +
                           " <==> " +
                           newFilename + str)
            } else {
               logger.info(it.getFullFilePath() + " " + it.modified.convertToLocalZone().toStr() +
                           " <==> " +
                           newFilename + str)
            }
            count.incrementAndGet()
         }

      if (Config.INST.verbose) {
         logger.info("${count.get()} results")
      }
   }

   private fun createNotExistingFile(existingFile: File, newFilename: String): File {
      var newFile = File(existingFile.parent + File.separator + newFilename)
      val extension = if (newFile.extension != "") "." + newFile.extension else ""
      val base = if (extension != "") newFile.toString().removeSuffix(extension) else newFile.toString()
      var i = 2
      while (newFile.exists()) {
         newFile = File(base + "_$i" + extension)
         i++
      }
      return newFile
   }

   private fun createFilename(it: FileLocation): String {
      return it.modified.ignoreMillis().convertToLocalZone().toStrSys()
                .replace(Regex("[:]"), "")
                .replace('-', '_') + "." + it.extension
   }

}