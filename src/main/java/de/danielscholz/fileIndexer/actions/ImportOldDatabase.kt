package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.FileContent
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.IndexRun
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.common.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.listOf

class ImportOldDatabase(val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)


   data class OldBackupRun(override var id: Long,
                           @NoDbProperty
                           override var pl: PersistenceLayer,
                           var pathPrefix: String,
                           var backupDate: Long) : EntityBase

   data class OldBackupPath(override var id: Long,
                            @NoDbProperty
                            override var pl: PersistenceLayer,
                            @CustomCharset("iso-8859-1")
                            var path: String) : EntityBase

   data class OldFileContent(override var id: Long,
                             @NoDbProperty
                             override var pl: PersistenceLayer,
                             var fileSize: Long,
                             var modified: Long,
                             val sha1: String) : EntityBase

   data class OldLocation(override var id: Long,
                          @NoDbProperty
                          override var pl: PersistenceLayer,
                          var fileContentId: Long,
                          var backupPathId: Long,
                          var backupRunId: Long,
                          @CustomCharset("iso-8859-1")
                          var filename: String,
                          @NoDbProperty
                          var fileContent: OldFileContent? = null,
                          @NoDbProperty
                          var backupPath: OldBackupPath? = null) : EntityBase


   class OldPersistenceLayer(db: Database) : PersistenceLayer(db) {

      fun getAllBackupRun(): List<OldBackupRun> {
         return db.dbQuery(
               "SELECT ${getSqlAttributes(OldBackupRun::class, "r")}" +
               " FROM BackupRun r " +
               " ORDER BY r.backupDate ASC ") {
            extractToEntity(OldBackupRun::class, it, "r")!!
         }
      }

      fun getAllLocations(backupRunId: Long): List<OldLocation> {
         return db.dbQuery("SELECT ${getSqlAttributes(OldLocation::class, "l")}, " +
                           "${getSqlAttributes(OldFileContent::class, "c")}, " +
                           "${getSqlAttributes(OldBackupPath::class, "p")} " +
                           "FROM FileLocation l " +
                           "     left outer join FileContent c on (l.fileContent_id = c.id) " +
                           "     join BackupPath p on (l.backupPath_id = p.id) " +
                           "WHERE l.backupRun_id = ? " +
                           "ORDER BY p.path ", listOf(backupRunId)) {
            val location = extractToEntity(OldLocation::class, it, "l")!!
            location.backupPath = extractToEntity(OldBackupPath::class, it, "p")!!
            location.fileContent = extractToEntity(OldFileContent::class, it, "c")!!
            location
         }
      }
   }


   fun import(oldDbFile: File, mediumSerial: String?, mediumDescription: String?) {
      logger.debug("Database: $oldDbFile")

      Database(oldDbFile.toString()).tryWith { oldDb ->

         val oldPl = OldPersistenceLayer(oldDb)
         if (oldPl.db.dbQueryUniqueStr("PRAGMA integrity_check").toLowerCase() != "ok") {
            logger.error("Datenbank ist nicht konsistent! Beende Programm.")
            throw Exception("Datenbank ist nicht konsistent! Beende Programm.")
         }
         oldDb.dbExecNoResult("PRAGMA cache_size=-8000") // 8 MB
         oldDb.dbExecNoResult("PRAGMA foreign_keys=ON")

         transaction(logger, pl.db) {

            val fileContentMap = mutableMapOf<String, Long>()

            pl.db.dbQuery("SELECT ${getFileContentSqlAttr("c")} FROM FileContent c ") {
               pl.extractFileContent(it, "c")
            }.forEach {
               if (it != null) fileContentMap[it.hash + "@" + it.fileSize] = it.id
            }

            val fileLocationMap = mutableMapOf<String, FileLocation>()

            pl.db.dbQuery("SELECT ${getFileLocationSqlAttr("l")}, c.hash as hash, c.fileSize as fileSize FROM FileLocation l join FileContent c on (l.fileContent_id = c.id) ") {
               Pair(pl.extractFileLocation(it, "l")!!, it.getString("hash") + "@" + it.getLong("fileSize"))
            }.forEach {
               fileLocationMap[it.second] = it.first
            }

            oldPl.getAllBackupRun().forEach { backupRun ->

               val dir = File(oldDbFile.parent + backupRun.pathPrefix.ensurePrefix("/")) // pathPrefix ist nur Datum, d.h. "2000-01-02"
               val pathWithoutPrefix = calcPathWithoutPrefix(dir)
               val filePath = pl.getOrInsertFullFilePath(File(pathWithoutPrefix))

               val indexRunId = pl.insertIntoIndexRun(
                     IndexRun(0,
                              pl,
                              filePath.id,
                              pathWithoutPrefix,
                              calcFilePathPrefix(dir),
                              "",
                              "",
                              "",
                              mediumDescription,
                              mediumSerial,
                              false,
                              (backupRun.backupDate * 1000).toInstant(),
                              false,
                              true,
                              null,
                              0,
                              0,
                              false)).id

               oldPl.getAllLocations(backupRun.id).forEach { location ->

                  var fileContentId = -1L

                  val key = location.fileContent!!.sha1 + "@" + location.fileContent!!.fileSize

                  fileContentMap[key]?.let { id ->
                     fileContentId = id
                  }

                  if (fileContentId < 0 && location.fileContent!!.fileSize > 0) {
                     fileContentId = pl.insertIntoFileContent(
                           FileContent(0,
                                       pl,
                                       location.fileContent!!.fileSize,
                                       location.fileContent!!.sha1,
                                       location.fileContent!!.sha1.getSha1Chunk(),
                                       location.fileContent!!.sha1.getSha1Chunk())).id

                     fileContentMap[key] = fileContentId
                  }

                  val filePathId = pl.getOrInsertFullFilePath(File(location.backupPath!!.path)).id

                  val modified = (location.fileContent!!.modified * 1000).toInstant()

                  pl.insertIntoFileLocation(
                        FileLocation(0,
                                     pl,
                                     fileContentId,
                                     filePathId,
                                     indexRunId,
                                     location.filename,
                                     location.filename.getFileExtension(),
                                     modified,
                                     modified,
                                     false,
                                     false,
                                     null))

               }
            }
         }
      }
   }

}