package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.ifTrue
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullPath
import org.slf4j.LoggerFactory

class FilterFiles {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(files: List<FileLocation>, pathFilter: String?, fileFilter: String?, isJavaRegex: Boolean, minSize: Long?, maxSize: Long?): List<FileLocation> {

      val filter = listOf((pathFilter != null).ifTrue("path filter $pathFilter", ""),
                          (fileFilter != null).ifTrue("file filter $fileFilter", ""),
                          (minSize != null).ifTrue("min file size $minSize", ""),
                          (maxSize != null).ifTrue("max file size $maxSize", ""))

      logger.info("Filtering ${files.size} files with " + filter.filter { it.isNotEmpty() }.joinToString(" and "))

      val pathRegex = getRegex(pathFilter, isJavaRegex)
      val fileRegex = getRegex(fileFilter, isJavaRegex)

      val filtered = files.filter {
         if (pathFilter != null) {
            pathRegex?.matches(it.getFullPath()) ?: it.getFullPath().contains(pathFilter)
         } else true
      }.filter {
         if (fileFilter != null) {
            fileRegex?.matches(it.filename) ?: it.filename == fileFilter
         } else true
      }.filter {
         if (minSize != null) {
            it.fileContent?.fileSize ?: 0 >= minSize
         } else true
      }.filter {
         if (maxSize != null) {
            it.fileContent?.fileSize ?: 0 <= maxSize
         } else true
      }

      logger.info("${filtered.size} files passed filter")
      return filtered
   }

   private fun getRegex(pathFilter: String?, isJavaRegex: Boolean): Regex? {
      return when {
         pathFilter == null       -> {
            null
         }
         isJavaRegex              -> {
            Regex(pathFilter)
         }
         pathFilter.contains('*') -> {
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

            Regex(pathFilterEscaped
                     .replace("**", "@@@@@@@@")
                     .replace("*", "[^/]*")
                     .replace("@@@@@@@@", ".*"))
         }
         else                     -> null
      }
   }

}