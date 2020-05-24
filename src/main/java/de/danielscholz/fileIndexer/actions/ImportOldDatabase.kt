package de.danielscholz.fileIndexer.actions

import com.google.common.collect.ListMultimap
import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.*
import de.danielscholz.fileIndexer.persistence.common.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.collections.listOf

class ImportOldDatabase(val pl: PersistenceLayer, val oldDbFile: File, val mediumSerial: String?, val mediumDescription: String?) {

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

      fun getAllBackupRuns(): List<OldBackupRun> {
         return db.dbQuery(
               "SELECT ${getSqlAttributes(OldBackupRun::class, "r")}" +
               " FROM BackupRun r " +
               " ORDER BY r.backupDate ASC ") { // oldest first
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
                           "ORDER BY p.path, l.filename ", listOf(backupRunId)) {
            val location = extractToEntity(OldLocation::class, it, "l")!!
            location.backupPath = extractToEntity(OldBackupPath::class, it, "p")!!
            location.fileContent = extractToEntity(OldFileContent::class, it, "c")!!
            location
         }
      }
   }

   private var maxReferenceInode = 0L

   fun import() {
      logger.debug("Import old database: $oldDbFile")

      Config.INST.maxTransactionSize = 20000
      Config.INST.maxTransactionDurationSec = 5 * 60

      maxReferenceInode = pl.db.dbQueryUniqueLongNullable(Queries.fileLocation3) ?: 0L

      Database(oldDbFile.toString()).tryWith { oldDb ->

         val oldPl = OldPersistenceLayer(oldDb)
         if (oldPl.db.dbQueryUniqueStr("PRAGMA integrity_check").toLowerCase() != "ok") {
            logger.error("ERROR: Datenbank ist nicht konsistent! Beende Programm.")
            throw Exception("Datenbank ist nicht konsistent! Beende Programm.")
         }
         oldDb.dbExecNoResult("PRAGMA cache_size=-8000") // 8 MB
         oldDb.dbExecNoResult("PRAGMA foreign_keys=ON")

         transaction(logger, pl.db) {
            importIntern(oldPl)
         }
      }
   }

   private fun importIntern(oldPl: OldPersistenceLayer) {

      // read existing data from target database --------------------------

      val indexRunMap = mutableMapOf<String, IndexRun>()

      pl.db.dbQuery("SELECT ${getIndexRunSqlAttr("r")} FROM IndexRun r Where r.isBackup = 1") {
         val indeRun = pl.extractIndexRun(it, "r")!!
         indexRunMap[getIndexRunKey(indeRun)] = indeRun
         indeRun
      }

      val fileContentMap = mutableMapOf<String, Long>()

      pl.db.dbQuery("SELECT ${getFileContentSqlAttr("c")} FROM FileContent c ") {
         val fileContent = pl.extractFileContent(it, "c")!!
         fileContentMap[fileContent.hash + "@" + fileContent.fileSize] = fileContent.id
         fileContent
      }

      val fileLocationMap = mutableListMultimapOf<String, FileLocation>()

      // selects only files with file size > 0
      pl.db.dbQuery("SELECT ${getFileLocationSqlAttr("l")}, c.hash as hash, c.fileSize as fileSize " +
                    "FROM FileLocation l join FileContent c on (l.fileContent_id = c.id) join IndexRun r on (r.id = l.indexRun_id) " +
                    "WHERE r.isBackup = 1 ") {
         val fileLocation = pl.extractFileLocation(it, "l")!!
         val fileSize = it.getLong("fileSize")
         val hash = it.getString("hash")
         fileLocationMap[hash + "@" + fileSize] = fileLocation
         fileLocation
      }

      // import old data -----------------------------

      oldPl.getAllBackupRuns().forEach { backupRun ->
         importBackupRun(backupRun, indexRunMap, fileContentMap, fileLocationMap, oldPl)
      }
   }

   private fun importBackupRun(backupRun: OldBackupRun,
                               indexRunMap: MutableMap<String, IndexRun>,
                               fileContentMap: MutableMap<String, Long>,
                               fileLocationMap: ListMultimap<String, FileLocation>,
                               oldPl: OldPersistenceLayer) {

      val fileLocationsConstrainsExisting = HashSet<String>()
      val dir = File(oldDbFile.parent + backupRun.pathPrefix.ensurePrefix("/")) // pathPrefix ist nur Datum, d.h. "2000-01-02"
      val pathWithoutPrefix = calcPathWithoutPrefix(dir.path)
      val filePath = pl.getOrInsertFullFilePath(File(pathWithoutPrefix))

      logger.info("Importing $dir")

      if (!dir.isDirectory) {
         logger.error("$dir does not exists!")
         return
      }

      val mediumSerialDetermined = mediumSerial ?: getVolumeSerialNr(dir.path)
                                   ?: throw Exception("MediumSerial of $dir could not be determined. Please specify mediumSerial explicit.")

      var indexRun = IndexRun(0,
                              pl,
                              filePath.id,
                              pathWithoutPrefix,
                              calcFilePathPrefix(dir.path),
                              "",
                              "",
                              "",
                              mediumDescription,
                              mediumSerialDetermined,
                              false,
                              (backupRun.backupDate * 1000).toInstant(),
                              false,
                              true,
                              null,
                              0,
                              0,
                              true)
      val indexRunKey = getIndexRunKey(indexRun)
      if (indexRunMap.containsKey(indexRunKey)) {
         val indexRun_ = indexRunMap[indexRunKey]!!
         if (indexRun.mediumSerial != indexRun_.mediumSerial ||
             indexRun.filePathId != indexRun_.filePathId) {
            logger.error("IndexRun is different!!")
            return
         }
         indexRun_.failureOccurred = true
         pl.updateIndexRun(indexRun_)
         indexRun = indexRun_
      } else {
         indexRun = pl.insertIntoIndexRun(indexRun)
         indexRunMap[indexRunKey] = indexRun
      }
      val indexRunId = indexRun.id

      val allLocations = oldPl.getAllLocations(backupRun.id)
      logger.info("Backup contains ${allLocations.size} entries.")

      allLocations.forEach { location ->

         var fileContentId: Long? = null
         var inode: Long? = null

         val key = location.fileContent!!.sha1 + "@" + location.fileContent!!.fileSize
         val fileOld = File(dir.toString().ensureSuffix("/") + location.backupPath!!.path.ensureSuffix("/") + location.filename)

         if (!fileOld.isFile) {
            logger.error("$fileOld does not exists!")
            return@forEach
         }

         val filePathId = pl.getOrInsertFullFilePath(File(location.backupPath!!.path)).id

         val keyConstraint = location.filename + "|" + filePathId
         if (fileLocationsConstrainsExisting.contains(keyConstraint)) {
            return@forEach
         }
         fileLocationsConstrainsExisting.add(keyConstraint)

         fileContentMap[key]?.let { id ->
            fileContentId = id
         }

         if (location.fileContent!!.fileSize > 0) {
            if (fileContentId == null) {
               fileContentId = pl.insertIntoFileContent(
                     FileContent(0,
                                 pl,
                                 location.fileContent!!.fileSize,
                                 location.fileContent!!.sha1,
                                 location.fileContent!!.sha1.getSha1Chunk(),
                                 location.fileContent!!.sha1.getSha1Chunk())).id

               fileContentMap[key] = fileContentId!!
            } else {
               for (fileLocation in fileLocationMap.get(key).sortedByDescending { it.indexRunId }) {
                  val file = File(fileLocation.getFullFilePathForTarget(indexRun))
                  if (file.isFile) {
                     if (Files.isSameFile(fileOld.toPath(), file.toPath())) {
                        if (fileLocation.referenceInode != null) {
                           inode = fileLocation.referenceInode
                        } else {
                           inode = ++maxReferenceInode
                           fileLocation.referenceInode = inode
                           pl.updateFileLocation(fileLocation)
                        }
                        break
                     }
                  } else logger.warn("$file does not exist")
               }
            }
         }

         val attributes = Files.readAttributes(fileOld.toPath(),
                                               BasicFileAttributes::class.java,
                                               LinkOption.NOFOLLOW_LINKS)

         val modified = (location.fileContent!!.modified * 1000).toInstant()
         val currentModified = attributes.lastModifiedTime().toInstant()
         val created = attributes.creationTime().toInstant()

         if (modified.ignoreMillis() != currentModified.ignoreMillis()) {
            logger.info("$fileOld has different modification date: ${modified.convertToLocalZone().toStr()} != ${currentModified.convertToLocalZone().toStr()}")
         }

         if (attributes.size() != location.fileContent!!.fileSize) {
            logger.error("$fileOld has different file size: ${attributes.size()} != ${location.fileContent!!.fileSize}")
         }

         val fileLocation = pl.insertIntoFileLocation(
               FileLocation(0,
                            pl,
                            fileContentId,
                            filePathId,
                            indexRunId,
                            location.filename,
                            location.filename.getFileExtension(),
                            created,
                            modified,
                            false,
                            false,
                            inode))

         if (location.fileContent!!.fileSize > 0) {
            fileLocationMap[key] = fileLocation
         }
      }

      indexRun.failureOccurred = false
      pl.updateIndexRun(indexRun)
      pl.db.commit()
   }

   private fun getIndexRunKey(indeRun: IndexRun) = indeRun.path + "@" + indeRun.runDate.ignoreMillis() + "@" + indeRun.mediumSerial

}