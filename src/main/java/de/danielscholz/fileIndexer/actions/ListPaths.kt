package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.persistence.LoadFileLocations
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import org.slf4j.LoggerFactory
import java.io.File

class ListPaths(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dir: File) {
      val path = pl.getNewestPath(null, dir)
      if (path != null) {
         ListIndexRuns(pl).format(path.indexRun, 0)
         logger.info("")
         val filePathsIds = LoadFileLocations(path, pl).load(false).map { it.filePathId }.toHashSet()
         filePathsIds
            .map { id -> pl.getFullRelPath(id) }
            .sorted()
            .forEach { fullRelPath ->
               logger.info(fullRelPath)
            }
      }
   }

}