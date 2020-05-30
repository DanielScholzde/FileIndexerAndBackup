package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.fileIndexer.common.ensureSuffix
import de.danielscholz.fileIndexer.common.formatOtherData
import de.danielscholz.fileIndexer.matching.MatchMode
import de.danielscholz.fileIndexer.matching.plus
import de.danielscholz.fileIndexer.matching.union
import de.danielscholz.fileIndexer.persistence.Queries.indexRun2
import de.danielscholz.fileIndexer.persistence.common.Database
import de.danielscholz.fileIndexer.persistence.common.EntityBase
import de.danielscholz.fileIndexer.persistence.common.getIndexRunSqlAttr
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.ResultSet
import kotlin.reflect.KClass

class PersistenceLayer(val db: Database) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   private val filePathCache = FilePathCache(this)

   private val base = PersistenceLayerBase(db)

   fun <T : EntityBase> extractToEntity(clazz: KClass<T>, result: ResultSet, prefix: String = ""): T? = base.extractToEntity(clazz, result, prefix, this)

   fun cleanupEntityCache() = base.cleanupEntityCache()

   // may only be called from FilePathCache!!
   fun insertIntoFilePath(filePath: FilePath) = base.insert(filePath, FilePath::class, ::validate)

   fun insertIntoFileLocation(fileLocation: FileLocation) = base.insert(fileLocation, FileLocation::class, ::validate)

   fun insertIntoFileContent(fileContent: FileContent) = base.insert(fileContent, FileContent::class, ::validate)

   fun insertIntoFileMeta(fileMeta: FileMeta) = base.insert(fileMeta, FileMeta::class, ::validate)

   fun insertIntoIndexRun(indexRun: IndexRun) = base.insert(indexRun, IndexRun::class, ::validate)

   fun updateFileLocation(fileLocation: FileLocation) = base.update(fileLocation, FileLocation::class, ::validate)

   fun updateIndexRun(indexRun: IndexRun) = base.update(indexRun, IndexRun::class, ::validate)

   fun extractIndexRun(result: ResultSet, prefix: String = "") = base.extractToEntity(IndexRun::class, result, prefix, this)

   fun extractFilePath(result: ResultSet, prefix: String = "") = base.extractToEntity(FilePath::class, result, prefix, this)

   fun extractFileLocation(result: ResultSet, prefix: String = "") = base.extractToEntity(FileLocation::class, result, prefix, this)

   fun extractFileContent(result: ResultSet, prefix: String = "") = base.extractToEntity(FileContent::class, result, prefix, this)

   fun extractFileMeta(result: ResultSet, prefix: String = "") = base.extractToEntity(FileMeta::class, result, prefix, this)

   fun loadAllIndexRun(failures: IndexRunFailures): List<IndexRun> {
      val cond = when (failures) {
         IndexRunFailures.INCL_FAILURES -> "1=1"
         IndexRunFailures.EXCL_FAILURES -> "r.failureOccurred = 0"
         IndexRunFailures.ONLY_FAILURES -> "r.failureOccurred = 1"
      }
      return db.dbQuery(
            "SELECT distinct ${getIndexRunSqlAttr("r")}" +
            " FROM IndexRun r " +
            " WHERE $cond " +
            " ORDER BY r.runDate DESC, r.id DESC", listOf()) {
         extractIndexRun(it, "r")!!
      }
   }

   /**
    * Searches within a given indexRun layer for a matching fileLocation-path prefix.
    */
   private fun hasIndexRunFilePath(indexRun: IndexRun, filePath: FilePath): Boolean {
      val result = db.dbQueryUniqueNullable(indexRun2, listOf(indexRun.id, filePath.path)) {
         extractIndexRun(it, "r")!!
      }
      return result != null
   }

   /**
    * Searches all indexRun layers for a matching indexRun (mediumSerial OR pathPrefix) und returns the result ordered by creation date (newest first).
    * E.g. search for pathWithoutPrefix=/test/sub/ and mediumSerial=2FA3 may find following indexRuns:
    * IndexRun(path=/test/     , mediumSerial=2FA3)
    * IndexRun(path=/test/sub/ , mediumSerial=2FA3)
    */
   fun findAllIndexRun(mediumSerial: String?, pathWithoutPrefix: String, pathPrefix: String?, failures: IndexRunFailures): List<IndexRun> {
      if (mediumSerial == null && pathPrefix == null) throw IllegalStateException()
      if (mediumSerial != null && pathPrefix != null) throw IllegalStateException()

      val cond = when (failures) {
         IndexRunFailures.INCL_FAILURES -> ""
         IndexRunFailures.EXCL_FAILURES -> "r.failureOccurred = 0 AND"
         IndexRunFailures.ONLY_FAILURES -> "r.failureOccurred = 1 AND"
      }

      val params = mutableListOf<String>()

      val cond2 = if (mediumSerial != null) {
         params.add(mediumSerial + "%")
         "r.mediumSerial like ?"
      } else {
         params.add(pathPrefix!!)
         "lower(r.pathPrefix) = lower(?)"
      }

      return db.dbQuery(
            "SELECT distinct ${getIndexRunSqlAttr("r")} " +
            " FROM IndexRun r " +
            " WHERE " +
            "       $cond " +
            "       $cond2 " +
            " ORDER BY r.runDate DESC, r.id DESC ", params) {
         extractIndexRun(it, "r")!!
      }.filter { indexRun ->
         pathWithoutPrefix.startsWith(indexRun.path) ||
         (indexRun.isBackup &&
          pathWithoutPrefix.ensureSuffix("/") == indexRun.path.substring(0, indexRun.path.lastIndexOf('/', indexRun.path.lastIndex - 1) + 1))
      }
   }

   fun getIndexRun(indexRunId: Long): IndexRun? {
      // implemented a short path to the cache to prevent making a database query
      return base.getEntity(IndexRun::class, indexRunId) {
         db.dbQueryUniqueNullable(Queries.indexRun1, listOf(indexRunId)) {
            extractIndexRun(it)!!
         }
      }
   }

   /**
    * Loads the FileLocation with the given ID and load additional the objects FileContent and FileMeta.
    */
   fun getFileLocation(fileLocationId: Long): FileLocation {
      val fileLocation1 = db.dbQueryUnique(Queries.fileLocation1, listOf(fileLocationId)) {
         val fileLocation = extractFileLocation(it, "l")!!
         val fileContent = extractFileContent(it, "c")
         val fileMeta = extractFileMeta(it, "m")
         fileLocation.fileContent = fileContent
         fileContent?.fileMeta = fileMeta
         fileLocation
      }
      fileLocation1.indexRun = getIndexRun(fileLocation1.indexRunId)
      return fileLocation1
   }

   fun searchOrInsertFilePath(filePath: FilePath) = filePathCache.searchOrInsertFilePath(filePath)

   fun getOrInsertFullFilePath(dir: File) = filePathCache.getOrInsertFullFilePath(dir)

   fun getFilePath(dir: File) = filePathCache.getFilePath(dir)

   fun getFilePath(filePathId: Long) = filePathCache.getFilePath(filePathId)

   fun clearFilePathCache(filePath: FilePath? = null) = filePathCache.clearFilePathCache(filePath)

   fun getNewestPath(path: MyPath, excludeIndexRunsWithFailures: Boolean = false): IndexRunFilePathResult? {
      val failures = if (excludeIndexRunsWithFailures) IndexRunFailures.EXCL_FAILURES else IndexRunFailures.INCL_FAILURES
      val list = getPathIntern(path, true, failures, null)
      return if (list.isEmpty()) null else list[0]
   }

   fun getPathList(path: MyPath, excludeFailures: Boolean = false): List<IndexRunFilePathResult> {
      val failures = if (excludeFailures) IndexRunFailures.EXCL_FAILURES else IndexRunFailures.INCL_FAILURES
      return getPathIntern(path, false, failures, null)
   }

   fun getFailurePathList(path: MyPath, fromIndexRunId: Long? = null): List<IndexRunFilePathResult> {
      return getPathIntern(path, false, IndexRunFailures.ONLY_FAILURES, fromIndexRunId)
   }

   /**
    * Searches all indexRuns for a matching indexed path. Returns either all results or only the first one (when singleResult=true).
    */
   private fun getPathIntern(path: MyPath,
                             singleResult: Boolean,
                             failures: IndexRunFailures,
                             fromIndexRunId: Long?): List<IndexRunFilePathResult> {
      val result = mutableListOf<IndexRunFilePathResult>()
      val pathPrefix = if (path.mediumSerial == null) path.prefix else null
      val dirWithoutPrefix = path.pathWithoutPrefix
      for (indexRun in findAllIndexRun(path.mediumSerial, dirWithoutPrefix, pathPrefix, failures)) {
         if (fromIndexRunId != null && indexRun.id < fromIndexRunId) {
            continue
         }
         val relPath = "/" + if (dirWithoutPrefix.startsWith(indexRun.path)) dirWithoutPrefix.removePrefix(indexRun.path) else ""  // todo find better solution in case of backup
         val filePath = getFilePath(File(relPath))
         if (filePath != null) {
            if (filePath.id == Queries.filePathRootId) {
               result.add(IndexRunFilePathResult(indexRun, filePath, this))
               if (singleResult) break
            } else {
               if (hasIndexRunFilePath(indexRun, filePath)) {
                  result.add(IndexRunFilePathResult(indexRun, filePath, this))
                  if (singleResult) break
               }
            }
         }
      }
      if (Config.INST.verbose) {
         logger.info("Found ${result.size} index runs for path $path (single result requested: $singleResult)")
      }
      return result
   }

   /** returns the complete path with prefix */
   fun getFullPath(indexRun: IndexRun, filePathId: Long?): String {
      val relPath = if (filePathId != null) getFullRelPath(filePathId) else null
      return indexRun.pathPrefix + indexRun.path + (relPath?.removePrefix("/") ?: "")
   }

   /** returns the complete path without prefix */
   fun getFullPathExclPrefix(indexRun: IndexRun, filePathId: Long): String {
      val relPath = getFullRelPath(filePathId)
      return indexRun.path + relPath.removePrefix("/")
   }

   /** returns the complete path with prefix and filename */
   fun getFullFilePath(indexRun: IndexRun, fileLocation: FileLocation): String {
      return getFullPath(indexRun, fileLocation.filePathId) + fileLocation.filename
   }

   /** returns the relative path beginning from indexRun root directory. Path begins and ends always with a / */
   fun getFullRelPath(filePathId: Long): String {
      return filePathCache.getFilePath(filePathId).path
   }

   fun loadFileLocationsForPath(path: MyPath,
                                excludeIndexRunsWithFailures: Boolean = false,
                                inclFilesInArchives: Boolean = true): Collection<FileLocation> {

      val indexRunFilePathResult = getNewestPath(path, excludeIndexRunsWithFailures) ?: return emptyList()
      return LoadFileLocations(this).load(indexRunFilePathResult, inclFilesInArchives)
   }

   fun loadFileLocationsForPaths(paths: List<MyPath>,
                                 excludeIndexRunsWithFailures: Boolean = false,
                                 inclFilesInArchives: Boolean = true): Collection<FileLocation> {

      val indexRunFilePathResults = paths.mapNotNull { dir -> getNewestPath(dir, excludeIndexRunsWithFailures) }
      if (indexRunFilePathResults.isEmpty()) return emptyList()

      return indexRunFilePathResults
         .map { indexRunFilePathResult -> LoadFileLocations(this).load(indexRunFilePathResult, inclFilesInArchives) }
         // todo empty result can not be reduced!
         .reduce { fileLocations1, fileLocations2 ->
            fileLocations1.union(fileLocations2,
                                 MatchMode.HASH + MatchMode.FILE_SIZE + MatchMode.FULL_PATH_EXCL_PREFIX + MatchMode.FILENAME, true)
         }
   }

   fun getObjectCount(): String {
      return filePathCache.getObjectCount() + "\n" + base.getObjectCount()
   }
}

/** returns the complete path with prefix and filename */
fun FileLocation.getFullFilePath(): String {
   return this.pl.getFullFilePath(this.indexRun!!, this)
}

/** returns the complete path with prefix and filename */
fun FileLocation.getFullFilePathForTarget(targetIndexRun: IndexRun): String {
   return this.pl.getFullFilePath(targetIndexRun, this)
}

/** returns the complete path with prefix */
fun FileLocation.getFullPath(): String {
   return this.pl.getFullPath(this.indexRun!!, this.filePathId)
}

/** returns the complete path without prefix */
fun FileLocation.getFullPathExclPrefix(): String {
   return this.pl.getFullPathExclPrefix(this.indexRun!!, this.filePathId)
}

fun FileLocation.getMediumDescrFullFilePathAndOtherData(): String {
   val mediumDescr1 = if (this.indexRun!!.mediumDescription != null) "[${this.indexRun!!.mediumDescription}]" else ""
   return mediumDescr1 + this.getFullFilePath() + this.formatOtherData()
}

fun IndexRun.loadFileLocations(inclFilesInArchives: Boolean = true): Collection<FileLocation> {
   return LoadFileLocations(this.pl).load(this, inclFilesInArchives)
}

fun IndexRun.loadFileContents(inclFilesInArchives: Boolean = true): Collection<FileContent> {
   return LoadFileContents(this.pl).load(this, inclFilesInArchives)
}

fun IndexRunFilePathResult.loadFileLocations(inclFilesInArchives: Boolean = true): Collection<FileLocation> {
   return LoadFileLocations(this.pl).load(this, inclFilesInArchives)
}

fun IndexRunFilePathResult.loadFileContents(inclFilesInArchives: Boolean = true): Collection<FileContent> {
   return LoadFileContents(this.pl).load(this, inclFilesInArchives)
}

enum class IndexRunFailures {
   INCL_FAILURES, EXCL_FAILURES, ONLY_FAILURES
}

data class IndexRunFilePathResult(val indexRun: IndexRun, val filePath: FilePath, val pl: PersistenceLayer)