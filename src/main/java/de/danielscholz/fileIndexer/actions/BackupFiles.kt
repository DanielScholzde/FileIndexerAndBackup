package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.matching.MatchMode.*
import de.danielscholz.fileIndexer.persistence.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import javax.swing.JOptionPane

// todo copy database to backup medium after backup process
// todo GUI with progressbar
// todo same hash, but different modification date -> config. parameter, what should happen
class BackupFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   private class Action(val path: String, val action: () -> Unit)

   private var maxReferenceInode = 0L

   private val matchMode = HASH + FILE_SIZE + MODIFIED_SEC

   /**
    * @param targetDir target directory (without date)
    * @param indexArchiveContents Should the archives in the source directory be indexed?
    */
   fun run(sourceDir: File,
           targetDir: File,
           mediumDescriptionSource: String?,
           mediumDescriptionTarget: String?,
           mediumSerialSource: String?,
           mediumSerialTarget: String?,
           indexArchiveContents: Boolean,
           skipIndexFilesOfSourceDir: Boolean,
           timeZone: TimeZone?,
           maxParallelReadsGeneral: Int,
           maxParallelReadsSmallFiles: Int,
           smallFilesThreshold: Int) {

      if (!testWriteableAndHardlinkSupport(targetDir)) return

      if (skipIndexFilesOfSourceDir) {
         logger.info("Skip: Create index of source directory $sourceDir")
      } else {
         logger.info("Create index of source directory $sourceDir")
         IndexFiles(sourceDir,
                    null,
                    mediumDescriptionSource,
                    mediumSerialSource,
                    timeZone,
                    indexArchiveContents,
                    false,
                    maxParallelReadsGeneral,
                    maxParallelReadsSmallFiles,
                    smallFilesThreshold,
                    pl).run()
      }

      val newestIndexRunSource = pl.getNewestPath(getVolumeSerialNr(sourceDir, mediumSerialSource), sourceDir, true)!!
      val sourceFiles = LoadFileLocations(newestIndexRunSource, pl).load(false)

      val mediumSerialT = getVolumeSerialNr(targetDir, mediumSerialTarget)

      val existingBackupsIndexRun = pl.getPathList(mediumSerialT, targetDir, false)

      var allNotEmptyExistingBackupFiles: Collection<FileLocation> = listOf()
      var newestExistingBackupFiles: Collection<FileLocation> = listOf()

      existingBackupsIndexRun.forEach {
         val files = LoadFileLocations(it, pl).load(false)

         if (newestExistingBackupFiles.isEmpty() && !it.indexRun.failureOccurred) {
            newestExistingBackupFiles = files
         }

         allNotEmptyExistingBackupFiles = allNotEmptyExistingBackupFiles.union(files.filterEmptyFiles(), REL_PATH2 + FILENAME + HASH + FILE_SIZE + MODIFIED_MILLIS, true)
      }

      testIfCancel(pl.db)

      if (!checkModifiedCountAndShowConfirmDialog(sourceFiles, newestExistingBackupFiles, allNotEmptyExistingBackupFiles, targetDir)) return

      val now = Instant.now()
      val targetSubDir = File("$targetDir/${now.ignoreMillis().convertToLocalZone().toStrFilename()}").canonicalFile

      if (!Config.dryRun) {
         if (targetSubDir.isDirectory) throw Exception("Backup directory $targetSubDir already exists!")
         Files.createDirectories(targetSubDir.toPath())
      }

      transaction(logger, pl.db) {

         maxReferenceInode = pl.db.dbQueryUniqueLongNullable(Queries.fileLocation3) ?: 0L

         val pathWithoutPrefix = calcPathWithoutPrefix(targetSubDir)
         val filePath = pl.getOrInsertFullFilePath(File(pathWithoutPrefix))

         val indexRunTarget = IndexRun(0,
                                       pl,
                                       filePath.id,
                                       pathWithoutPrefix,
                                       calcFilePathPrefix(targetSubDir),
                                       newestIndexRunSource.indexRun.excludedPaths,
                                       newestIndexRunSource.indexRun.excludedFiles,
                                       mediumDescriptionTarget,
                                       mediumSerialT,
                                       testCaseSensitive(targetDir),
                                       now,
                                       false,
                                       true,
                                       if (Config.createHashOnlyForFirstMb) 1 else null,
                                       Files.getFileStore(targetDir.toPath()).totalSpace,
                                       -1, // updated after backup completed
                                       true) // set to false after backup completed without failure

         if (!Config.dryRun) {
            pl.insertIntoIndexRun(indexRunTarget)
         }

         val alreadyExistingFilesInBackup: Collection<Pair<FileLocation, FileLocation>> =
               sourceFiles.intersect(allNotEmptyExistingBackupFiles.makeUnique(matchMode, false), matchMode, true)

         val changedFiles: Collection<FileLocation> = sourceFiles - alreadyExistingFilesInBackup.map { it.first }

         val copiedFiles = mutableMapOf<String, FileLocation>()

         logger.info("Create hardlinks. quantity: ${alreadyExistingFilesInBackup.size}, total size: ${alreadyExistingFilesInBackup.mapNotNull { it.first.fileContent?.fileSize }.sum().formatAsFileSize()}")
         logger.info("Copy changed files. quantity: ${changedFiles.size}, total size: ${changedFiles.mapNotNull { it.fileContent?.fileSize }.sum().formatAsFileSize()}")
         logger.info("")

         val actions = mutableListOf<Action>()

         alreadyExistingFilesInBackup.forEach { (sourceFileLocation, existingBackupFileLocation) ->
            actions += Action(pl.getFullRelPath(sourceFileLocation.filePathId) + sourceFileLocation.filename) {
               createHardlink(indexRunTarget, sourceFileLocation, existingBackupFileLocation, indexRunTarget)
            }
         }

         changedFiles.forEach { sourceFileLocation ->
            actions += Action(pl.getFullRelPath(sourceFileLocation.filePathId) + sourceFileLocation.filename) {

               val key = createKey(sourceFileLocation, matchMode)

               val alreadyCopiedFileLocation = copiedFiles[key]
               if (alreadyCopiedFileLocation != null) {
                  createHardlink(indexRunTarget, sourceFileLocation, alreadyCopiedFileLocation, indexRunTarget)
               } else {
                  copiedFiles[key] = createCopy(indexRunTarget, sourceFileLocation, indexRunTarget)
               }
            }
         }

         var currentPath: String? = null
         actions.sortedBy { it.path }.forEach {
            val path = it.path.substring(0, it.path.lastIndexOf("/"))
            if (path != currentPath) {
               currentPath = path
               logger.info(sourceDir.path + currentPath?.replace('/', File.separatorChar))
            }
            it.action()
         }

         if (!Config.dryRun) {
            indexRunTarget.failureOccurred = false
            indexRunTarget.usableSpace = Files.getFileStore(targetDir.toPath()).usableSpace
            pl.updateIndexRun(indexRunTarget)
         }
      }

      Global.echoAndResetStat()
   }


   @Suppress("UNUSED_VALUE")
   private fun checkModifiedCountAndShowConfirmDialog(sourceFiles: Collection<FileLocation>,
                                                      newestExistingBackupFiles: Collection<FileLocation>,
                                                      allNotEmptyExistingBackupFiles: Collection<FileLocation>,
                                                      targetDir: File): Boolean {

      val pathAndFilename = REL_PATH2 + FILENAME

      fun subtract(sourceFilesCopy: Collection<FileLocation>, intersection: Collection<FileLocation>): Collection<FileLocation> {
         return sourceFilesCopy.subtract(intersection, pathAndFilename, false)
      }

      fun leftPad(num: Int, length: Int): String {
         return StringUtils.leftPad(num.toString(), length)
      }

      var sourceFilesCopy = sourceFiles
      var newestExistingBackupFilesCopy = newestExistingBackupFiles

      var unchanged = sourceFilesCopy.intersect(newestExistingBackupFilesCopy, REL_PATH2 + FILENAME + FILE_SIZE + HASH, false)
      val unchangedFiles = unchanged.left()

      sourceFilesCopy = subtract(sourceFilesCopy, unchangedFiles)
      newestExistingBackupFilesCopy = subtract(newestExistingBackupFilesCopy, unchanged.right())
      unchanged = listOf() // free memory


      var changed = sourceFilesCopy.intersect(newestExistingBackupFilesCopy, pathAndFilename, false)
      val changedFiles = changed.left()

      sourceFilesCopy = subtract(sourceFilesCopy, changedFiles)
      newestExistingBackupFilesCopy = subtract(newestExistingBackupFilesCopy, changed.right())
      changed = listOf() // free memory

      var renamed = sourceFilesCopy.intersect(newestExistingBackupFilesCopy, REL_PATH2 + FILE_SIZE + HASH, true)
      val renamedFiles = renamed.left().makeUnique(pathAndFilename)

      sourceFilesCopy = subtract(sourceFilesCopy, renamedFiles)
      newestExistingBackupFilesCopy = subtract(newestExistingBackupFilesCopy, renamed.right().makeUnique(pathAndFilename))
      renamed = listOf() // free memory

      var moved = sourceFilesCopy.intersect(newestExistingBackupFilesCopy, FILENAME + FILE_SIZE + HASH, true)
      val movedFiles = moved.left().makeUnique(pathAndFilename)

      sourceFilesCopy = subtract(sourceFilesCopy, movedFiles)
      newestExistingBackupFilesCopy = subtract(newestExistingBackupFilesCopy, moved.right().makeUnique(pathAndFilename))
      moved = listOf() // free memory

      var renamedAndMoved = sourceFilesCopy.intersect(newestExistingBackupFilesCopy, FILE_SIZE + HASH, true)
      val renamedAndMovedFiles = renamedAndMoved.left().makeUnique(pathAndFilename)

      sourceFilesCopy = subtract(sourceFilesCopy, renamedAndMovedFiles)
      newestExistingBackupFilesCopy = subtract(newestExistingBackupFilesCopy, renamedAndMoved.right().makeUnique(pathAndFilename))
      renamedAndMoved = listOf() // free memory

      val newFiles = sourceFilesCopy

      val deletedFiles = newestExistingBackupFilesCopy

      val totalNumberOfFiles = sourceFiles.size + deletedFiles.size
      val changedNumberOfFiles = changedFiles.size + renamedFiles.size + movedFiles.size + renamedAndMovedFiles.size + deletedFiles.size

      val sourceFilesUnique = sourceFiles.makeUnique(matchMode)
      val allNotEmptyExistingBackupFilesUnique = allNotEmptyExistingBackupFiles.makeUnique(matchMode)
      val diskspaceNeeded = sourceFilesUnique.subtract(allNotEmptyExistingBackupFilesUnique, matchMode, false).fileSize()
      val alreadyExistingInBackup = sourceFilesUnique.intersect(allNotEmptyExistingBackupFilesUnique, matchMode, false).firstFileSize()

      val fileStore = Files.getFileStore(targetDir.toPath())
      val usableSpace = fileStore.usableSpace
      val totalSpace = fileStore.totalSpace

      val maxLength = totalNumberOfFiles.toString().length

      logger.info("Free diskspace:                ${totalSpace.formatAsFileSize()}\n" +
                  "Total amount to backup:        ${sourceFilesUnique.fileSize().formatAsFileSize()}\n" +
                  "Diskspace needed:              ${diskspaceNeeded.formatAsFileSize()}\n" +
                  "Already present in the backup: ${alreadyExistingInBackup.formatAsFileSize()}\n" +
                  "\n" +
                  "Unchanged files:       ${leftPad(unchangedFiles.size, maxLength)} (${unchangedFiles.fileSize().formatAsFileSize()})\n" +
                  "New files:             ${leftPad(newFiles.size, maxLength)} (${newFiles.fileSize().formatAsFileSize()})\n" +
                  "Changed files:         ${leftPad(changedFiles.size, maxLength)} (${changedFiles.fileSize().formatAsFileSize()})\n" +
                  "Renamed files:         ${leftPad(renamedFiles.size, maxLength)} (${renamedFiles.fileSize().formatAsFileSize()})\n" +
                  "Moved files:           ${leftPad(movedFiles.size, maxLength)} (${movedFiles.fileSize().formatAsFileSize()})\n" +
                  "Renamed+moved files:   ${leftPad(renamedAndMovedFiles.size, maxLength)} (${renamedAndMovedFiles.fileSize().formatAsFileSize()})\n" +
                  "Deleted files:         ${leftPad(deletedFiles.size, maxLength)} (${deletedFiles.fileSize().formatAsFileSize()})\n" +
                  "Total (excl. deleted): ${leftPad(sourceFiles.size, maxLength)} (${sourceFiles.fileSize().formatAsFileSize()})\n")

      if (usableSpace - diskspaceNeeded < totalSpace / 100 * Config.minDiskFreeSpacePercent
          || usableSpace - diskspaceNeeded < Config.minDiskFreeSpaceMB * 1024L * 1024) {

         val msg = "Not enough free space on the target medium available.\n" +
                   "Required is ${diskspaceNeeded.formatAsFileSize()}, but only ${usableSpace.formatAsFileSize()} is available.\n" +
                   "Backup process is not started."

         if (Config.silent) {
            logger.error(msg)
            return false
         }
         JOptionPane.showConfirmDialog(null, msg, "Information", JOptionPane.OK_CANCEL_OPTION)
         return false
      }

      if (Config.silent) return true

      val changedPercent = if (totalNumberOfFiles > 0) changedNumberOfFiles * 100 / totalNumberOfFiles else 0
      if (changedPercent >= Config.maxChangedFilesWarningPercent
          && changedNumberOfFiles > Config.minAllowedChanges) {
         val dialogResult = JOptionPane.showConfirmDialog(
               null,
               "More files were changed or deleted than allowed\n" +
               "(changed: ${changedFiles.size}, deleted: ${deletedFiles.size}. This corresponds to: $changedPercent%). " +
               "Do you want to continue the backup process?",
               "Confirmation",
               JOptionPane.YES_NO_OPTION)
         if (dialogResult != JOptionPane.YES_OPTION) {
            return false
         }
      }

      val dialogResult = JOptionPane.showConfirmDialog(
            null,
            "A backup will be created for ${sourceFiles.size} files (${sourceFilesUnique.fileSize().formatAsFileSize()}) (diskspace needed: ${diskspaceNeeded.formatAsFileSize()}).\n" +
            "New: ${newFiles.size} (${newFiles.fileSize().formatAsFileSize()})\n" +
            "Changed: ${changedFiles.size} (${changedFiles.fileSize().formatAsFileSize()})\n" +
            "Renamed/moved: ${renamedFiles.size + movedFiles.size + renamedAndMovedFiles.size} (${(renamedFiles.fileSize() + movedFiles.fileSize() + renamedAndMovedFiles.fileSize()).formatAsFileSize()})\n" +
            "Deleted: ${deletedFiles.size} (${deletedFiles.fileSize().formatAsFileSize()})\n" +
            "Unchanged: ${unchangedFiles.size} (${unchangedFiles.fileSize().formatAsFileSize()})\n" +
            "Do you want to continue the backup process?",
            "Confirmation",
            JOptionPane.YES_NO_OPTION)
      if (dialogResult != JOptionPane.YES_OPTION) {
         return false
      }

      return true
   }


   private fun createCopy(indexRunTarget: IndexRun,
                          sourceFileLocation: FileLocation,
                          indexRun: IndexRun): FileLocation {

      val newBackupFile = File(sourceFileLocation.getFullFilePathForTarget(indexRunTarget))
      val sourceFile = File(sourceFileLocation.getFullFilePath())

      if (!Config.dryRun) {
         newBackupFile.parentFile.mkdirs()
         Files.copy(sourceFile.toPath(), newBackupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
         logger.trace("copy: $sourceFile --> $newBackupFile")
         Global.createdFilesCallback(newBackupFile)
      }

      val fileLocation = FileLocation(0,
                                      pl,
                                      sourceFileLocation.fileContentId,
                                      sourceFileLocation.filePathId,
                                      indexRun.id,
                                      sourceFileLocation.filename,
                                      sourceFileLocation.extension,
                                      sourceFileLocation.created,
                                      sourceFileLocation.modified,
                                      sourceFileLocation.hidden,
                                      sourceFileLocation.inArchive,
                                      null,
                                      indexRun,
                                      sourceFileLocation.fileContent)

      if (!Config.dryRun) {
         return pl.insertIntoFileLocation(fileLocation)
      } else {
         return fileLocation
      }
   }


   private fun createHardlink(indexRunTarget: IndexRun,
                              sourceFileLocation: FileLocation,
                              existingBackupFileLocation: FileLocation,
                              indexRun: IndexRun) {

      val newBackupFile = File(sourceFileLocation.getFullFilePathForTarget(indexRunTarget))
      val existingBackupFile = File(existingBackupFileLocation.getFullFilePath())

      if (!Config.dryRun) {
         newBackupFile.parentFile.mkdirs()
         Files.createLink(newBackupFile.toPath(), existingBackupFile.toPath())
         logger.trace("link: $existingBackupFile --> $newBackupFile")
         Global.createdFilesCallback(newBackupFile)

         if (existingBackupFileLocation.referenceInode == null) {
            existingBackupFileLocation.referenceInode = ++maxReferenceInode
            pl.updateFileLocation(existingBackupFileLocation)
         }

         pl.insertIntoFileLocation(
               FileLocation(0,
                            pl,
                            sourceFileLocation.fileContentId,
                            sourceFileLocation.filePathId,
                            indexRun.id,
                            sourceFileLocation.filename,
                            sourceFileLocation.extension,
                            existingBackupFileLocation.created,
                            existingBackupFileLocation.modified,
                            existingBackupFileLocation.hidden,
                            existingBackupFileLocation.inArchive,
                            existingBackupFileLocation.referenceInode,
                            indexRun,
                            existingBackupFileLocation.fileContent))
      }
   }

   private fun testCaseSensitive(targetDir: File): Boolean {
      if (targetDir.name.toLowerCase() != targetDir.name.toUpperCase()) {
         if (File(targetDir.path.toLowerCase()).isDirectory && File(targetDir.path.toUpperCase()).isDirectory) {
            return false
         }
      }
      return true
   }

   private fun testWriteableAndHardlinkSupport(targetDir: File): Boolean {
      val testFile = File("$targetDir/test_73451974239d43.dat")
      try {
         Files.write(testFile.toPath(), ByteArray(1))

         val testFile2 = File("$targetDir/test_9462734b348264.dat")
         try {
            Files.createLink(testFile2.toPath(), testFile.toPath())
         } catch (e: FileAlreadyExistsException) {
            logger.error("Test for hardlink support aborted: File $testFile2 already exists!")
            return false
         } catch (e: Exception) {
            logger.error("Path $targetDir does not support hardlinks!")
            return false
         } finally {
            testFile2.delete()
         }

      } catch (e: Exception) {
         logger.error("Path $targetDir is not writeable!")
         return false
      } finally {
         testFile.delete()
      }
      return true
   }

   fun Collection<FileLocation>.fileSize() = sumBy { it.fileContent?.fileSize ?: 0 }

   fun Collection<Pair<FileLocation, FileLocation>>.firstFileSize() = sumBy { it.first.fileContent?.fileSize ?: 0 }

}
