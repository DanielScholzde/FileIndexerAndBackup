package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.formatAsFileSize
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import org.slf4j.LoggerFactory

class DatabaseReport(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun getOverview() {
      logger.info("IndexRun-Count: " + pl.db.dbQueryUniqueLong("SELECT count(*) FROM IndexRun").toStr())
      logger.info("FileLocation-Count: " + pl.db.dbQueryUniqueLong("SELECT count(*) FROM FileLocation").toStr())
      logger.info("FileContent-Count: " + pl.db.dbQueryUniqueLong("SELECT count(*) FROM FileContent").toStr())
      logger.info("FilePath-Count: " + pl.db.dbQueryUniqueLong("SELECT count(*) FROM FilePath").toStr())
      logger.info("")

      val pageSize = pl.db.dbQueryUniqueLong("PRAGMA page_size")
      val pageCount = pl.db.dbQueryUniqueLong("PRAGMA page_count")
      val unusedCount = pl.db.dbQueryUniqueLong("PRAGMA freelist_count")
      logger.info("DB size: " + (pageCount * pageSize).formatAsFileSize())
      logger.info("Unused Pages: " + (unusedCount * 100 / pageCount) + "%")
      logger.info("Page size: " + pageSize.formatAsFileSize())
      logger.info("")
      logger.info("Integrity check: " + pl.db.dbQueryUniqueStr("PRAGMA integrity_check"))
      logger.info("Encoding: " + pl.db.dbQueryUniqueStr("PRAGMA encoding"))
      logger.info("auto_vacuum: " + pl.db.dbQueryUniqueStr("PRAGMA auto_vacuum"))
      logger.info("automatic_index: " + pl.db.dbQueryUniqueStr("PRAGMA automatic_index"))
      logger.info("cache_size: " + getCacheSize())
      //logger.info("case_sensitive_like: " + pl.db.dbQueryUniqueStr("PRAGMA case_sensitive_like"))
      logger.info("journal_mode: " + pl.db.dbQueryUniqueStr("PRAGMA journal_mode"))
      logger.info("synchronous: " + getSynchronous())
      logger.info("mmap_size: " + pl.db.dbQueryUniqueStr("PRAGMA mmap_size"))
      //logger.info("Stats: \n" + queryDebug("PRAGMA stats", pl.db))
      //Global.db.dbQueryUniqueStr("VACUUM")
   }

   private fun getSynchronous(): String {
      var str = pl.db.dbQueryUniqueStr("PRAGMA synchronous")
      when (str) {
         "0" -> {
            str += " (OFF)"
         }
         "1" -> {
            str += " (Normal)"
         }
         "2" -> {
            str += " (FULL)"
         }
         "3" -> {
            str += " (EXTRA)"
         }
      }
      return str
   }

   private fun getCacheSize(): String {
      var str = pl.db.dbQueryUniqueStr("PRAGMA cache_size")
      if (str.startsWith("-")) str = str.removePrefix("-") + " KB"
      return str
   }

}