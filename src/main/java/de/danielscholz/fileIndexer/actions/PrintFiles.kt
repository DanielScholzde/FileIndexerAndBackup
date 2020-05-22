package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.PrintFilesParams
import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import de.danielscholz.fileIndexer.persistence.getFullPath
import org.slf4j.LoggerFactory

class PrintFiles(private val params: PrintFilesParams) {

   private val logger = LoggerFactory.getLogger(javaClass)

   fun run(files: List<FileLocation>) {
      if (params.folderOnly) {
         if (params.details) {
            files.asSequence().map { it.getFullPath() to it }.groupingBy { it.first }.fold(0L) { accumulator: Long, element: Pair<String, FileLocation> ->
               accumulator + (element.second.fileContent?.fileSize ?: 0L)
            }.entries.sortedBy { it.key }.forEach {
               logger.info("${it.key} ${it.value.formatAsFileSize()}")
            }
         } else {
            files.asSequence().map { it.getFullPath() to it }.distinctBy { it.first }.sortedBy { it.first }.forEach {
               logger.info(it.first)
            }
         }
      } else {
         files.forEach {
            logger.info(it.getFullFilePath())
         }
      }
   }
}