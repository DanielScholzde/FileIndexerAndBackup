package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.fileIndexer.persistence.LoadFileLocations
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import org.slf4j.LoggerFactory

class ListPaths(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(path: MyPath) {
      val indexRunFilePathResult = pl.getNewestPath(path)
      if (indexRunFilePathResult != null) {
         ListIndexRuns(pl).format(indexRunFilePathResult.indexRun, null)
         logger.info("")
         val filePathsIds = LoadFileLocations(pl).load(indexRunFilePathResult, false).map { it.filePathId }.toHashSet()
         filePathsIds
            .map { id -> pl.getFullRelPath(id) }
            .sorted()
            .forEach { fullRelPath ->
               logger.info(fullRelPath)
            }
      }
   }

}