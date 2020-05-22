package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.PrintFilesParams
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getFullPath
import org.slf4j.LoggerFactory

class PrintFiles(val params: PrintFilesParams) {

   private val logger = LoggerFactory.getLogger(javaClass)

   fun run(files: List<FileLocation>) {
      if (params.folderOnly) {
         files.asSequence().map { it.getFullPath() to it }.distinctBy { it.first }.sortedBy { it.first }.forEach {
            logger.info(it.first)
         }
      } else {
         files.forEach {
            logger.info(it.getFullFilePath())
         }
      }
   }
}