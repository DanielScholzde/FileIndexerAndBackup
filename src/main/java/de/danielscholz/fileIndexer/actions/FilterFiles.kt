package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory

class FilterFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, pathFilter: String): List<FileLocation> {
      logger.info("Filtering ${files.size} files with filter: $pathFilter")

      var regex: Regex? = null
      if (pathFilter.contains('*')) {
         regex = Regex(pathFilter
                          .replace("**", "@@@@@@@@")
                          .replace("*", "[^/]*")
                          .replace("@@@@@@@@", ".*"))
      }

      val filtered = files.filter {
         val fullFilePath = it.getFullFilePath()
         if (regex != null) {
            regex.matches(fullFilePath)
         } else {
            fullFilePath.contains(pathFilter)
         }
      }

      logger.info("${filtered.size} files passed filter")
      return filtered
   }

}