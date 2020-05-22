package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.CompareIndexRunsParams
import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.matching.MatchMode.*
import de.danielscholz.fileIndexer.matching.plus
import de.danielscholz.fileIndexer.matching.subtract
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class CompareIndexRuns(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(indexRunId1: Long, indexRunId2: Long, result: CompareIndexRunsParams.CompareIndexRunsResult?): List<FileLocation>? {
      val indexRun1 = pl.getIndexRun(indexRunId1)!!
      val files1 = LoadFileLocations(IndexRunFilePathResult(indexRun1, pl.getFilePath(Queries.filePathRootId)), pl)
         .load(false).asSequence()

      val indexRun2 = pl.getIndexRun(indexRunId2)!!
      val files2 = LoadFileLocations(IndexRunFilePathResult(indexRun2, pl.getFilePath(Queries.filePathRootId)), pl)
         .load(false).asSequence()

      val diff1 = files1.subtract(files2, REL_PATH2 + FILENAME + HASH + FILE_SIZE, false).toList()
      val diff2 = files2.subtract(files1, REL_PATH2 + FILENAME + HASH + FILE_SIZE, false).toList()
      val diff = diff1 + diff2

      if (Config.INST.verbose) {
         logger.info("There are the following differences:")
      }

      val count = AtomicInteger()
      diff.forEach {
         if (Config.INST.verbose) {
            logger.info(it.getMediumDescrFullFilePathAndOtherData())
         } else {
            logger.info(it.getFullFilePath())
         }
         count.incrementAndGet()
      }

      if (Config.INST.verbose) {
         logger.info("${count.get()} results")
      }

      return when (result) {
         CompareIndexRunsParams.CompareIndexRunsResult.LEFT  -> {
            diff1
         }
         CompareIndexRunsParams.CompareIndexRunsResult.RIGHT -> {
            diff2
         }
         CompareIndexRunsParams.CompareIndexRunsResult.BOTH  -> {
            diff
         }
         else                                                -> {
            null
         }
      }
   }
}