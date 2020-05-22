package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullFilePath

class FilterFiles {

   fun run(files: List<FileLocation>, pathFilter: String): List<FileLocation> {

      return files.filter { it.getFullFilePath().contains(pathFilter) }
   }

}