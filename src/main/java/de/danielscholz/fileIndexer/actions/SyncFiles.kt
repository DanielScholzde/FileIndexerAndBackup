package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.matching.*
import de.danielscholz.fileIndexer.matching.MatchMode.*
import de.danielscholz.fileIndexer.matching.ResultFilter.*
import de.danielscholz.fileIndexer.persistence.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane

// todo recognize renamed / moved files like in BackupFiles
class SyncFiles(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(sourceDir: File,
           targetDir: File,
           includedPaths: List<String>,
           mediumDescriptionSource: String?,
           mediumDescriptionTarget: String?,
           mediumSerialSource: String?,
           mediumSerialTarget: String?,
           indexArchiveContentsOfSourceDir: Boolean,
           skipIndexFilesOfSourceDir: Boolean,
           sourceReadConfig: IndexFiles.ReadConfig,
           targetReadConfig: IndexFiles.ReadConfig) {

      if (skipIndexFilesOfSourceDir) {
         logger.info("Skip: Create index of source directory $sourceDir")
      } else {
         logger.info("Create index of source directory $sourceDir")
         IndexFiles(sourceDir,
                    includedPaths,
                    null,
                    mediumDescriptionSource,
                    mediumSerialSource,
                    indexArchiveContentsOfSourceDir,
                    false,
                    sourceReadConfig,
                    pl).run()
      }

      val newestIndexRunSource = pl.getNewestPath(getVolumeSerialNr(sourceDir, mediumSerialSource), sourceDir, true)!!
      val sourceFiles = LoadFileLocations(newestIndexRunSource, pl).load(false)

      val mediumSerialT = getVolumeSerialNr(targetDir, mediumSerialTarget)

      // todo should be possible to deactivate indexing via parameter
      val indexRunTargetList = pl.findAllIndexRun(mediumSerialT,
                                                  calcPathWithoutPrefix(targetDir),
                                                  if (mediumSerialT.isEmpty()) calcFilePathPrefix(targetDir) else null,
                                                  IndexRunFailures.EXCL_FAILURES)
      // if target is not indexed yet, create an index
      if (indexRunTargetList.isEmpty()) {
         Files.createDirectories(targetDir.toPath())
         logger.info("Index target directory") // todo excluded FilePath should not be considered here!
         IndexFiles(targetDir,
                    listOf(),
                    null,
                    mediumDescriptionTarget,
                    mediumSerialTarget,
                    false,
                    false,
                    targetReadConfig,
                    pl).run()
      } else {
         logger.info("Verify files within target directory")
         if (!VerifyFiles(pl, false).run(targetDir, true)) {
            logger.warn("Files within target directory have changed! Create new index.")
            IndexFiles(targetDir,
                       listOf(),
                       null,
                       mediumDescriptionTarget,
                       mediumSerialTarget,
                       false,
                       false,
                       targetReadConfig,
                       pl).run()
         }
      }
      val newestIndexRunTarget = pl.getNewestPath(mediumSerialT, targetDir)!!
      val targetFiles = LoadFileLocations(newestIndexRunTarget, pl).load(false)

      val matchingMode = REL_PATH2 + FILENAME
      var newFiles = sourceFiles.subtract(targetFiles, matchingMode, false)
      // changed files have a different modification date, a different hash or file size
      var changedFiles = sourceFiles.intersect(targetFiles, matchingMode, false).filter(MODIFIED_SEC_NEQ or FILE_SIZE_NEQ or HASH_NEQ)
      var deletedFiles = targetFiles.subtract(sourceFiles, matchingMode, false)

      testIfCancel(pl.db)

      val totalNumberOfFiles = sourceFiles.union(targetFiles, HASH + FILE_SIZE + REL_PATH2 + FILENAME, true).size
      val numberOfChangedFiles = changedFiles.size + deletedFiles.size
      val changedPercent = if (totalNumberOfFiles > 0) numberOfChangedFiles * 100 / totalNumberOfFiles else 0
      if (Config.INST.confirmations
          && changedPercent >= Config.INST.maxChangedFilesWarningPercent
          && numberOfChangedFiles > Config.INST.minAllowedChanges) {
         val dialogResult = JOptionPane.showConfirmDialog(
               null,
               "More files were changed or deleted than allowed\n" +
               "(changed: ${changedFiles.size}, deleted: ${deletedFiles.size}. This corresponds to : $changedPercent%). " +
               "Do you want to continue the sync process?",
               "Confirmation",
               JOptionPane.YES_NO_OPTION)
         if (dialogResult != JOptionPane.YES_OPTION) {
            return
         }
      }

      logger.info("Copy changed files (${changedFiles.size} ${changedFiles.sumBy { it.first.fileContent?.fileSize ?: 0 }.formatAsFileSize()})")
      for (changedFile in changedFiles) {
         val fileSource = File(changedFile.first.getFullFilePath())
         val fileTarget = File(changedFile.first.getFullFilePathForTarget(newestIndexRunTarget.indexRun))
         Files.copy(fileSource.toPath(), fileTarget.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
         Global.stat.copiedFilesCount++
         Global.stat.copiedFilesSize += changedFile.first.fileContent?.fileSize ?: 0
         testIfCancel(pl.db)
      }
      @Suppress("UNUSED_VALUE")
      changedFiles = listOf() // free memory

      logger.info("Delete files (${deletedFiles.size} ${deletedFiles.sumBy { it.fileContent?.fileSize ?: 0 }.formatAsFileSize()})")
      for (deletedFile in deletedFiles) {
         val file = File(deletedFile.getFullFilePath())
         Files.delete(file.toPath())
         Global.stat.deletedFilesCount++
         Global.stat.deletedFilesSize += deletedFile.fileContent?.fileSize ?: 0
         testIfCancel(pl.db)
      }
      @Suppress("UNUSED_VALUE")
      deletedFiles = listOf() // free memory

      logger.info("Copy new files (${newFiles.size} ${newFiles.sumBy { it.fileContent?.fileSize ?: 0 }.formatAsFileSize()})")
      for (newFile in newFiles) {
         val fileSource = File(newFile.getFullFilePath())
         val fileTarget = File(newFile.getFullFilePathForTarget(newestIndexRunTarget.indexRun))
         val fullTargetDir = fileTarget.parentFile
         if (!fullTargetDir.isDirectory) Files.createDirectories(fullTargetDir.toPath())
         Files.copy(fileSource.toPath(), fileTarget.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
         Global.stat.copiedFilesCount++
         Global.stat.copiedFilesSize += newFile.fileContent?.fileSize ?: 0
         testIfCancel(pl.db)
      }
      @Suppress("UNUSED_VALUE")
      newFiles = listOf() // free memory

      Global.echoAndResetStat()

      logger.info("Index target directory")
      IndexFiles(targetDir,
                 listOf(),
                 null,
                 mediumDescriptionTarget,
                 mediumSerialTarget,
                 false,
                 false,
                 targetReadConfig,
                 pl).run()

      // todo compare source and target directory
   }

}
