package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory

class FilterFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, pathFilter: String): List<FileLocation> {
      logger.info("Filtering ${files.size} files with filter: $pathFilter")
      val filtered = files.filter { it.getFullFilePath().contains(pathFilter) }
      logger.info("${filtered.size} files passed filter")
      return filtered
   }

}