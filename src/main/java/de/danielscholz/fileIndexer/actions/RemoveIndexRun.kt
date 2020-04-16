package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.convertToLocalZone
import de.danielscholz.fileIndexer.common.listOf
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.common.transaction
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.Queries.delFileContent
import de.danielscholz.fileIndexer.persistence.Queries.delFileLocation
import de.danielscholz.fileIndexer.persistence.Queries.delFileMeta
import de.danielscholz.fileIndexer.persistence.Queries.delFilePath
import de.danielscholz.fileIndexer.persistence.Queries.delIndexRun
import org.slf4j.LoggerFactory

class RemoveIndexRun(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun removeIndexRun(indexRunId: Long) {
      transaction(logger, pl.db) {
         logger.info("Remove index layer no. $indexRunId from " + pl.getIndexRun(indexRunId)?.runDate?.convertToLocalZone()?.toStr())

         val fileLocationCount = pl.db.dbExec(delFileLocation, listOf(indexRunId))
         logger.info("$fileLocationCount file references removed")

         val indexRunCount = pl.db.dbExec(delIndexRun, listOf(indexRunId))
         logger.info("$indexRunCount index layers removed")

         val fileMetaCount = pl.db.dbExec(delFileMeta)
         logger.info("$fileMetaCount file metadata removed")

         val fileContentCount = pl.db.dbExec(delFileContent)
         logger.info("$fileContentCount file content data removed")

         var filePathCountLast: Long
         var filePathCountSum = 0L
         do {
            filePathCountLast = pl.db.dbExec(delFilePath)
            filePathCountSum += filePathCountLast
         } while (filePathCountLast > 0)
         logger.info("$filePathCountSum paths removed")
      }
   }

}