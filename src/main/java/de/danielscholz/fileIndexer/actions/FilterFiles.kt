package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory

class FilterFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, pathFilter: String, isJavaRegex: Boolean): List<FileLocation> {
      logger.info("Filtering ${files.size} files with filter: $pathFilter")

      var regex: Regex? = null
      if (isJavaRegex) {
         regex = Regex(pathFilter)
      } else {
         if (pathFilter.contains('*')) {
            val pathFilterEscaped = pathFilter
               .replace("[", "\\[")
               .replace("]", "\\]")
               .replace("{", "\\{")
               .replace("}", "\\}")
               .replace("(", "\\(")
               .replace(")", "\\)")
               .replace("|", "\\|")
               .replace(".", "\\.")
               .replace("?", "\\?")

            regex = Regex(
               pathFilterEscaped
                  .replace("**", "@@@@@@@@")
                  .replace("*", "[^/]*")
                  .replace("@@@@@@@@", ".*")
            )
         }
      }

      val filtered = files.filter {
         val fullFilePath = it.getFullFilePath()
         regex?.matches(fullFilePath) ?: fullFilePath.contains(pathFilter)
      }

      logger.info("${filtered.size} files passed filter")
      return filtered
   }

}