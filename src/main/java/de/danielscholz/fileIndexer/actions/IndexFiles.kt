package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.gui.InfoPanel
import de.danielscholz.fileIndexer.img.ImgUtils.extractExifOriginalDateAndDimension
import de.danielscholz.fileIndexer.img.ImgUtils.extractThumbnail
import de.danielscholz.fileIndexer.img.ImgUtils.scaleAndSaveImg
import de.danielscholz.fileIndexer.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import org.apache.commons.compress.archivers.ArchiveEntry
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.math.absoluteValue

/**
 * Indexes all files within the directory dir.
 * Files in archives are indexed if indexArchiveContents is true.
 */
class IndexFiles(
   private val dir: File,
   private val includedPaths: List<String>,
   private val lastIndexDir: File?,
   private val mediumDescription: String?,
   private val mediumSerial: String?,
   private val indexArchiveContents: Boolean,
   private val updateHardlinksInLastIndex: Boolean,
   private val readConfig: ReadConfig,
   private val pl: PersistenceLayer
) {

   class ReadConfig(val maxParallelReadsGeneral: Int, val maxParallelReadsSmallFilesFactor: Int, val smallFilesSizeThreshold: Int) {
      @Suppress("MemberVisibilityCanBePrivate")
      companion object {
         val hd = ReadConfig(1, 2, 10_000)
         val ssd = ReadConfig(2, 2, 500_000)
         val network = ReadConfig(1, 2, 2_000_000)
         val configs = mapOf("hd" to hd, "ssd" to ssd, "network" to network)
      }
   }

   private val logger = LoggerFactory.getLogger(javaClass)

   private data class Key(val fileSize: Long, val modified: Long)

   private val lastIndexedFileLocationsByKey = mutableListMultimapOf<Key, FileLocation>()

   private lateinit var indexRun: IndexRun
   private val indexedFileLocationIdsByKey = syncronizedMutableSetMultimapOf<Key, Long>() // to recognize hardlinks
   private var maxReferenceInode = 0L
   private var caseSensitiveFS = false
   private val numThreads = if (Config.INST.allowMultithreading) Runtime.getRuntime().availableProcessors() else 1

   private val stat = IndexFilesStats { getParallelReads() }

   private var channel = Channel<Pair<suspend () -> Unit, String>>(capacity = 10)


   fun run(): Pair<IndexRun, Int> {
      // reads the number of files, size of all files within the directory and determines excludedFilesUsed / excludedPathsUsed
      val dirInfos = readAllDirInfos(dir, indexArchiveContents, includedPaths)
      stat.filesSizeAll = dirInfos.size
      stat.filesCountAll = dirInfos.files
      caseSensitiveFS = dirInfos.caseSensitive

      val mediumSerialDetermined = getVolumeSerialNr(dir, mediumSerial)

      // load already indexed files into lastIndexedFileLocationsByKey
      loadAlreadyIndexedFiles(lastIndexDir ?: dir, dirInfos.excluded, mediumSerialDetermined)

      maxReferenceInode = pl.db.dbQueryUniqueLongNullable(Queries.fileLocation3) ?: 0L

      transaction(logger, pl.db) {
         val pathWithoutPrefix = calcPathWithoutPrefix(dir)
         val filePath = pl.getOrInsertFullFilePath(File(pathWithoutPrefix))
         val fileStore = Files.getFileStore(dir.toPath())

         indexRun = pl.insertIntoIndexRun(
            IndexRun(
               0,
               pl,
               filePath.id,
               pathWithoutPrefix,
               calcFilePathPrefix(dir),
               includedPaths.convertToSortedStr(),
               dirInfos.excluded.excludedPathsUsed.convertToSortedStr(),
               dirInfos.excluded.excludedFilesUsed.convertToSortedStr(),
               mediumDescription,
               mediumSerialDetermined,
               dirInfos.caseSensitive,
               Instant.now(),
               false,
               false,
               if (Config.INST.createHashOnlyForFirstMb) 1 else null,
               fileStore.totalSpace,
               fileStore.usableSpace,
               true // set to false at the end of a successful index process
            )
         )

         try {
            if (Config.INST.progressWindow) {
               InfoPanel.show(stat)
               stat.start()
            }

            runBlocking {
               supervisorScope { // needed?
                  withContext(Dispatchers.IO) {

                     repeat(times = min(numThreads, Config.INST.maxThreads)) {
                        launch {
                           for (processFileCallbackNoExc in channel) {
                              logger.trace("channel.received: {}", processFileCallbackNoExc.second)
                              processFileCallbackNoExc.first.invoke()
                           }
                        }
                     }

                     processFolderNoExc(dir, null, includedPaths.map { Path(it, it) })

                     channel.close()
                  }
               }
            }

            indexRun.failureOccurred = false
            pl.updateIndexRun(indexRun)
         } finally {
            pl.clearFilePathCache()
            if (Config.INST.progressWindow) {
               stat.stop()
               InfoPanel.close()
            }
         }
      }

      Global.echoAndResetStat()

      return indexRun to stat.indexedFilesCount.get()
   }

   private fun loadAlreadyIndexedFiles(dir: File, excluded: Excluded, mediumSerial: String?) {
      data class FLKey(val filePathId: Long, val filename: String)

      val pathsToLoad = mutableListOf<IndexRunFilePathResult>()
      // load all indexed files from newest successful created indexRun layer
      val newestPath = pl.getNewestPath(mediumSerial, dir, true)
      if (newestPath != null) {
         pathsToLoad.add(newestPath)
      }
      // if there are newer indexRun layer with a failure, load them too
      // if newestPath is null, all indexRun layer with failures are loaded
      val failurePathList = pl.getFailurePathList(mediumSerial, dir, newestPath?.indexRun?.id) // ordered by: newest first
      pathsToLoad.addAll(failurePathList.reversed())

      // load the successfully created indexRun, then load any newer indexRun layer with failures which might overwrite parts of already loaded file information
      val fileLocationMap = mutableMapOf<FLKey, FileLocation>()
      for (pathResult in pathsToLoad) {
         val fileLocationList = LoadFileLocations(pathResult, pl).load(true)
         for (fileLocation in fileLocationList) {
            fileLocationMap[FLKey(fileLocation.filePathId, fileLocation.filename)] = fileLocation
         }
      }

      for (fileLocation in fileLocationMap.values) {
         val key = Key(fileLocation.fileContent?.fileSize ?: 0L, fileLocation.modified.toEpochMilli())
         lastIndexedFileLocationsByKey[key] = fileLocation
      }
   }


   private suspend fun processFolderNoExc(sourceDir: File, parentFilePath: FilePath?, includedPaths: List<Path>) {
      logger.info("index: {}", sourceDir)

      val filePath = if (parentFilePath != null) {
         pl.searchOrInsertFilePath(
            FilePath(
               0,
               pl,
               parentFilePath.id,
               parentFilePath.path + sourceDir.name + "/",
               sourceDir.name,
               parentFilePath.depth + 1
            )
         )
      } else {
         pl.getFilePath(Queries.filePathRootId)
      }

      stat.currentProcessedFile = "Read directory $sourceDir"
      val (folders, files) = readDir(sourceDir, caseSensitiveFS, includedPaths)

      if (files.isNotEmpty()) {
         val sortedFiles = files
            .map { file -> file to file.length() } // read file size for every file
            .sortedByDescending { it.second } // order by file size descending to process bigger files first

         for ((file, _) in sortedFiles) {
            if (testIfCancelNoException(pl.db)) {
               break
            }

            val fileProcessor: suspend () -> Unit = {
               processFileTopLevelNoExc(file, filePath)
            }
            logger.trace("channel.send: {}", file.name)
            channel.send(fileProcessor to file.name)
         }
      }

      for (folder in folders) {
         if (testIfCancelNoException(pl.db)) {
            break
         }
         processFolderNoExc(folder.first, filePath, folder.second)
      }
   }

   private suspend fun processFileTopLevelNoExc(file: File, filePath: FilePath) {
      try {
         if (isArchiveToProcess(file)) {

            readSemaphore(file.name, file.length()) {
               val filePathCache = mutableMapOf<String, FilePath>()

               val archiveRead = processArchiveSuspending(
                  file,
                  processEntry = { stream, archiveEntry ->
                     processArchiveFile(file, stream, archiveEntry, filePath, filePathCache)
                  },
                  processFailure = { exception ->
                     val msg = "ERROR: $file: Content of archive could not be read. ${exception.javaClass.simpleName}: ${exception.message}"
                     Global.stat.failedFileReads.add(msg)
                     logger.error(msg)
                  })

               processNormalFile(file, filePath, archiveRead)
            }
         } else {
            readSemaphore(file.name, file.length()) {
               processNormalFile(file, filePath, false)
            }
         }
      } catch (e: CancelException) {
         // todo
      } catch (e: Exception) {
         // todo
      }
   }

   private suspend fun processNormalFile(file: File, filePath: FilePath, archiveRead: Boolean) {
      try {
         @Suppress("BlockingMethodInNonBlockingContext")
         val attributes = Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
         )

         val fileSize = attributes.size()

         myLazy {
            InputStreamWrapperImpl(BufferedInputStream(FileInputStream(file)))
         }.tryWith { lazyInputStream ->
            processFileStream(
               filePath,
               file.name,
               lazyInputStream,
               attributes.creationTime().toInstant(),
               attributes.lastModifiedTime().toInstant(),
               fileSize,
               file.isHidden,
               false,
               null,
               archiveRead
            )
         }
      } catch (e: IOException) {
         val msg = "ERROR: $file: File could not be read. ${e.javaClass.simpleName}: ${e.message}"
         Global.stat.failedFileReads.add(msg)
         logger.error(msg)
      }
   }

   private suspend fun processArchiveFile(
      archiveFile: File,
      stream: InputStreamWrapper,
      entry: ArchiveEntry,
      parentFilePath: FilePath,
      filePathCache: MutableMap<String, FilePath>
   ) {
      val pathAndFilename = entry.name.replace('\\', '/')
      val filename = pathAndFilename.substringAfterLast("/")
      val pathWithinArchive = pathAndFilename.substringBeforeLast("/", "")
      val path = parentFilePath.path + archiveFile.name + "/" + pathWithinArchive
      val filePath: FilePath
      if (path.isNotEmpty()) {
         if (filePathCache.containsKey(path)) {
            filePath = filePathCache[path]!!
         } else {
            filePath = pl.getOrInsertFullFilePath(File(path))
            filePathCache[path] = filePath
            //logger.info("{}", File("$archive/$pathWithinArchive"))
         }
      } else {
         filePath = pl.getFilePath(Queries.filePathRootId)
      }
      val modified = entry.lastModifiedDate.toInstant()

      return processFileStream(
         filePath,
         filename,
         myLazy { stream },
         modified,
         modified,
         entry.size,
         hidden = false,
         inArchive = true,
         archiveName = archiveFile.name,
         archiveRead = true
      )
   }

   private suspend fun processFileStream(
      filePath: FilePath,
      filename: String,
      lazyInputStream: Lazy<InputStreamWrapper>,
      created: Instant,
      modified: Instant,
      fileSize: Long,
      hidden: Boolean,
      inArchive: Boolean,
      archiveName: String?,
      archiveRead: Boolean
   ) {
      if (logger.isDebugEnabled) {
         logger.debug("index: {}, file size: {}{}", filename, fileSize.formatAsFileSize(), inArchive.ifTrue(" (archive: $archiveName)", ""))
      }

      var fileContentId: Long? = null
      var referenceInode: Long? = null

      if (fileSize > 0) {
         synchronized(filename, fileSize) {
            val result = createFileContent(
               lazyInputStream,
               fileSize,
               modified,
               filename,
               filePath,
               inArchive,
               archiveName
            )
            fileContentId = result.first
            referenceInode = result.second
         }
      }

      val fileLocation = pl.insertIntoFileLocation(
         FileLocation(
            0,
            pl,
            fileContentId,
            filePath.id,
            indexRun.id,
            filename,
            filename.getFileExtension(),
            created,
            modified,
            hidden,
            inArchive,
            referenceInode
         )
      )

      if (fileSize > 0 && !inArchive) {
         addAlreadyIndexedFile(fileLocation, fileSize, modified)
      }

      Global.stat.indexedFilesCount++
      Global.stat.indexedFilesSize += fileSize
      stat.indexedFilesCount++
      if (inArchive) {
         stat.indexedFilesSize += fileSize
      } else {
         if (!archiveRead) {
            stat.indexedFilesSize += fileSize
         }

         stat.indexedFilesCountNoArchive++
         stat.indexedFilesSizeNoArchive += fileSize
      }

      if (logger.isTraceEnabled) {
         logger.trace("END index: {}, file size: {}{}", filename, fileSize.formatAsFileSize(), inArchive.ifTrue(" (archive: $archiveName)", ""))
      }
   }

   /**
    * Erstellt einen FileContent-Eintrag, falls der Dateiinhalt noch nicht indiziert wurde.
    * Falls FAST_MODE aktiv ist, wird bei einer bereits "bekannten" Datei mit gleichem Namen und Änderungsdatum nicht
    * erneut der Hashwert berechnet.
    *
    * Wenn die Datei ein Bild ist, wird ein Thumbnail erstellt und ein FileMeta-Eintrag erzeugt.
    */
   private fun createFileContent(
      lazyInputStream: Lazy<InputStreamWrapper>,
      fileSize: Long,
      modified: Instant,
      filename: String,
      filePath: FilePath,
      inArchive: Boolean,
      archiveName: String?
   ): Pair<Long, Long?> {

      if (!inArchive) {
         var fileContentId: Long? = null
         var referenceInode: Long? = null
         var minOneOtherFileLocationWithSameInode = false
         val foundFileLocations = searchInAlreadyIndexedFilesForHardlinks(filePath, filename, fileSize, modified)
         if (foundFileLocations.isNotEmpty()) {
            val minExistingRefInode = foundFileLocations.mapNotNull { it.referenceInode }.minOfOrNull { it }
            for (fileLocation in foundFileLocations) {
               // update only the current indexRun layer except it is explicitly demanded
               if (updateHardlinksInLastIndex || fileLocation.indexRunId == indexRun.id) {
                  if (referenceInode == null) {
                     referenceInode = minExistingRefInode ?: ++maxReferenceInode
                  }
                  if (fileLocation.referenceInode != referenceInode) {
                     fileLocation.referenceInode = referenceInode
                     pl.updateFileLocation(fileLocation)
                  }
                  minOneOtherFileLocationWithSameInode = true
               }
               // all fileContentId's should be the same
               if (fileContentId != null && fileContentId != fileLocation.fileContentId) throw IllegalStateException()
               fileContentId = fileLocation.fileContentId
            }
         }
         if (fileContentId != null) {
            return fileContentId to minOneOtherFileLocationWithSameInode.ifTrue(referenceInode, null)
         }
      }

      val extension = filename.getFileExtension()?.lowercase()
      val lazyImgContent = if (extension in Config.INST.imageExtensions && fileSize < 50_000_000) {
         myLazy { ByteArray(fileSize.toInt()) }
      } else {
         null
      }

      val checksumCreator = myLazy {
         ChecksumCreator(lazyInputStream.value, fileSize, null, lazyImgContent?.value) // todo file?
      }
      val checksumFromBeginTemp = myLazy {
         checksumCreator.value.getChecksumFromBeginTemp().joinToString(",")
      }

      // if fastMode is active, the calculation of the complete hash may be omitted
      if (Config.INST.fastMode && !isAlwaysCheckHash(filename)) {
         for (lastIndexedFileLocation in lastIndexedFileLocationsByKey[Key(fileSize, modified.toEpochMilli())]) {
            if (lastIndexedFileLocation.filename == filename
               && compareFilePaths(lastIndexedFileLocation, filePath)
               && (Config.INST.ignoreHashInFastMode
                     || lastIndexedFileLocation.fileContent!!.hashBegin.startsWith(let {
                  stat.currentProcessedFile = if (archiveName != null) "$archiveName / $filename" else filename
                  checksumFromBeginTemp.value
               }
               ))
            ) {

               logger.trace("FAST_MODE: already indexed file found: $filename")
               Global.stat.fastModeHitCount++

               stat.currentProcessedFile = filename

               return lastIndexedFileLocation.fileContent!!.id to null
            }
         }
      }

      Global.stat.notFastModeHitCount++

      // calculate hash, expensive!
      val checksum = let {
         stat.currentProcessedFile = if (archiveName != null) "$archiveName / $filename" else filename
         checksumCreator.value.calcChecksum()
      }

      // query database for a matching fileContent with this hash
      var fileContent = pl.db.dbQueryUniqueNullable(Queries.fileContent1, listOf(checksum.sha1, fileSize)) {
         pl.extractFileContent(it)
      }

      if (fileContent != null) {
         // entry found; return this entry
         return fileContent.id to null
      }

      // otherwise create a new entry
      fileContent = pl.insertIntoFileContent(
         FileContent(
            0,
            pl,
            fileSize,
            checksum.sha1,
            checksum.sha1ChunksFromBeginning.joinToString(","),
            checksum.sha1ChunksFromEnd.joinToString(",")
         )
      )

      if (lazyImgContent != null && lazyImgContent.isInitialized()) {
         try {
            val imgAttr = extractExifOriginalDateAndDimension(lazyImgContent.value, Config.INST.timeZone)
            if (imgAttr.originalDate != null || imgAttr.width != null) {
               pl.insertIntoFileMeta(FileMeta(0, pl, fileContent.id, imgAttr.width, imgAttr.height, imgAttr.originalDate))
            }
            //logger.info("$filename: ${imgAttr.width}x${imgAttr.height} ${imgAttr.originalDate?.formatDE()}")
         } catch (e: Exception) {
            logger.warn("WARN: $filePath/$filename: EXIF infos could not be read. {}: {}", e.javaClass.simpleName, e.message)
         }
         if (Config.INST.createThumbnails) {
            saveThumbnail(extension, lazyImgContent.value, fileContent.id)
         }
      }

      Global.stat.newIndexedFilesCount++
      Global.stat.newIndexedFilesSize += fileSize

      return fileContent.id to null
   }

   private fun addAlreadyIndexedFile(
      fileLocation: FileLocation,
      fileSize: Long,
      modified: Instant
   ) {
      indexedFileLocationIdsByKey[Key(fileSize, modified.toEpochMilli())] = fileLocation.id
   }

   /**
    * Detection of hardlinks within the same indexRun layer or the layer before.
    * Returns all fileLocations whose file is a hard link to the specified file.
    */
   private fun searchInAlreadyIndexedFilesForHardlinks(
      filePath: FilePath,
      filename: String,
      fileSize: Long,
      modified: Instant
   ): List<FileLocation> {

      val potentiallySameFileLocations: List<FileLocation> = lastIndexedFileLocationsByKey[Key(fileSize, modified.toEpochMilli())]
      val potentiallySameFileLocationIds: Set<Long> = indexedFileLocationIdsByKey[Key(fileSize, modified.toEpochMilli())]

      if (potentiallySameFileLocationIds.isEmpty() && potentiallySameFileLocations.isEmpty()) {
         return listOf()
      }

      val currentFile = File(getCurrFullPath(filePath) + filename).toPath()

      fun isSameFile(potentiallySameFileLocation: FileLocation): Boolean {
         val file = File(
            potentiallySameFileLocation.indexRun!!.pathPrefix
                  + potentiallySameFileLocation.indexRun!!.path
                  + pl.getFilePath(potentiallySameFileLocation.filePathId).path.removePrefix("/")
                  + potentiallySameFileLocation.filename
         )

         if (!file.exists()) return false // if file is deleted or renamed meanwhile

         val potentiallySameFile = file.toPath()

         return Files.isSameFile(currentFile, potentiallySameFile)
      }

      return potentiallySameFileLocations.asSequence()
         .filter { potentiallySameFileLocation -> isSameFile(potentiallySameFileLocation) }
         .toList() +
            potentiallySameFileLocationIds.asSequence()
               .map { fileLocationId -> pl.getFileLocation(fileLocationId) }
               .filter { potentiallySameFileLocation -> isSameFile(potentiallySameFileLocation) }
               .toList()
   }

   private fun saveThumbnail(ext: String?, imgContent: ByteArray, fileContentId: Long) {
      val thumbnail: ByteArray?
      if (ext in Config.INST.rawImagesExtensions) {
         thumbnail = extractThumbnail(imgContent)
      } else {
         thumbnail = imgContent
      }
      if (thumbnail != null) {
         val bucket = ("" + (fileContentId / 1000)).leftPad(4, '0')
         val dbFile = File(pl.db.dbFile)
         var pathStr = "${dbFile.parent}${File.separator}.${dbFile.name}_thumbnails${File.separator}"
         var path = File(pathStr)
         if (!path.isDirectory) {
            path.mkdirs()
            Global.createdDirsCallback(path)
         }
         pathStr += "$bucket${File.separator}"
         path = File(pathStr)
         if (!path.isDirectory) {
            path.mkdir()
            Global.createdDirsCallback(path)
         }
         scaleAndSaveImg(thumbnail, "$pathStr$fileContentId.jpg")
      }
   }

   /**
    * Returns true if
    * a) relative path (starting from indexRun root directory) is the same
    * b) full path (incl. indexRun root directory without pathPrefix) is the same
    */
   private fun compareFilePaths(fileLocationLastIndex: FileLocation, filePath: FilePath): Boolean {
      // bei Fall a) muss nur die ID von FilePath verglichen werden
      if (fileLocationLastIndex.filePathId == filePath.id) {
         return true
      }
      if (!caseSensitiveFS) {
         val p1 = pl.getFilePath(fileLocationLastIndex.filePathId).path
         val p2 = filePath.path
         if (p1.length == p2.length && p1.lowercase() == p2.lowercase()) {
            return true
         }
      }

      // otherwise compare whole path
      val p1 = getFullPathWithoutPrefix(fileLocationLastIndex)
      val p2 = getCurrFullPathWithoutPrefix(filePath)
      if (p1 == p2) {
         return true
      }
      if (!caseSensitiveFS) {
         return p1.length == p2.length && p1.lowercase() == p2.lowercase()
      }
      return false
   }

   private fun getCurrFullPathWithoutPrefix(filePath: FilePath) =
      indexRun.path + filePath.path.removePrefix("/")

   private fun getCurrFullPath(filePath: FilePath) =
      indexRun.pathPrefix + getCurrFullPathWithoutPrefix(filePath)

   private fun getFullPathWithoutPrefix(fileLocation: FileLocation) =
      fileLocation.indexRun!!.path + pl.getFilePath(fileLocation.filePathId).path.removePrefix("/")

   private fun isAlwaysCheckHash(filename: String) = Config.INST.alwaysCheckHashOnIndexForFilesSuffix.any { filename.endsWith(it) }

   private fun isArchiveToProcess(file: File) = indexArchiveContents && file.extension.lowercase() in Config.INST.archiveExtensions

   private val mutexArray = Array(42) { Mutex() }

   /**
    * Mutex which prevents parallel processing of files with the same file size.
    * (Background: files with the same size could have the same content or it could be a hardlink)
    */
   private suspend fun <T> synchronized(filename: String, fileSize: Long, block: suspend () -> T): T {
      // ATTENTION: which lock to consider is determined by fileSize
      val id = fileSize.toByte().toInt().absoluteValue % mutexArray.size
      logger.trace("Mutex {} acquire with file size {}", id, fileSize)
      mutexArray[id].withLock(filename) {
         return block()
      }
   }

   private val readSemaphore = Semaphore(readConfig.maxParallelReadsGeneral)
   private val readSemaphoreSmall = Semaphore(readConfig.maxParallelReadsGeneral * readConfig.maxParallelReadsSmallFilesFactor)

   private suspend fun <T> readSemaphore(
      filename: String,
      fileSize: Long,
      block: suspend () -> T
   ): T {
      if (readConfig.smallFilesSizeThreshold == 0) {
         return readSemaphore.withPermit("General", filename) {
            block()
         }
      }

      suspend fun smallPermit(): T {
         when (readConfig.maxParallelReadsSmallFilesFactor) {
            1 -> return readSemaphoreSmall.withPermit("Small", filename) {
               block()
            }
            2 -> return readSemaphoreSmall.withPermit("Small", filename) {
//               readSemaphoreSmall.withPermit("Small", filename) {
               block()
//               }
            }
            3 -> return readSemaphoreSmall.withPermit("Small", filename) {
//               readSemaphoreSmall.withPermit("Small", filename) {
//                  readSemaphoreSmall.withPermit("Small", filename) {
               block()
//                  }
//               }
            }
            else -> throw Exception("Parameter maxParallelReadsSmallFilesFactor has invalid value")
         }
      }

      if (fileSize > readConfig.smallFilesSizeThreshold) {
         return readSemaphore.withPermit("General", filename) {
            smallPermit()
         }
      }

      return readSemaphoreSmall.withPermit("Small", filename) {
         block()
      }
   }

   private fun getParallelReads(): String {
      val general = readConfig.maxParallelReadsGeneral - readSemaphore.availablePermits
      val small = readConfig.maxParallelReadsGeneral * readConfig.maxParallelReadsSmallFilesFactor - readSemaphoreSmall.availablePermits
      return "" + (general + small - general * readConfig.maxParallelReadsSmallFilesFactor)
   }

   private suspend inline fun <T> Semaphore.withPermit(semaphoreName: String, filename: String, action: () -> T): T {
      logger.trace("Semaphore {} {} permit acquire", semaphoreName, filename)
      acquire()
      logger.trace("Semaphore {} {} permit acquired", semaphoreName, filename)
      try {
         return action()
      } finally {
         release()
         logger.trace("Semaphore {} {} permit released", semaphoreName, filename)
      }
   }

   private suspend inline fun <T> Mutex.withLock(name: String, action: () -> T): T {
      logger.trace("Mutex {} permit acquire", name)
      lock()
      logger.trace("Mutex {} permit acquired", name)
      try {
         return action()
      } finally {
         unlock()
         logger.trace("Mutex {} permit released", name)
      }
   }
}


