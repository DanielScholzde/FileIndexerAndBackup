package de.danielscholz.fileIndexer

import ch.qos.logback.classic.LoggerContext
import de.danielscholz.fileIndexer.actions.*
import de.danielscholz.fileIndexer.common.ensureSuffix
import de.danielscholz.fileIndexer.common.registerLowMemoryListener
import de.danielscholz.fileIndexer.common.registerShutdownCallback
import de.danielscholz.fileIndexer.common.tryWith
import de.danielscholz.fileIndexer.gui.InfopanelSwing
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.PrepareDb
import de.danielscholz.fileIndexer.persistence.Queries
import de.danielscholz.fileIndexer.persistence.common.Database
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.ArgParser
import de.danielscholz.kargparser.ArgParserBuilder
import de.danielscholz.kargparser.ArgParserConfig
import de.danielscholz.kargparser.parser.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

/**
 * Information about this program can be found in README.md
 */

private val logger = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
   main(args, {}, {})
}

/**
 * @param runBefore Action that is called before the main program. Does not take place within a transaction!
 * @param runAfter Action that is called after the main program. Does not take place within a transaction!
 */
internal fun main(args: Array<String>, runBefore: (PersistenceLayer) -> Unit = {}, runAfter: (PersistenceLayer) -> Unit = {}) {
   registerShutdownCallback {
      Global.cancel = true
      (LoggerFactory.getILoggerFactory() as LoggerContext).stop()
   }
   registerLowMemoryListener()

   val parser = createParser { globalParams: GlobalParams, command: (PersistenceLayer) -> Unit ->
      if (globalParams.db == null) globalParams.db = File("IndexedFiles")
      val inMemoryDb = globalParams.db!!.name == ":memory:"
      if (!inMemoryDb) {
         if (!globalParams.db!!.name.endsWith(".db")) {
            globalParams.db = File(globalParams.db.toString().ensureSuffix(".db")).canonicalFile
         } else {
            globalParams.db = globalParams.db!!.canonicalFile
         }
      }

      if (Config.verbose) logger.debug("Database: ${globalParams.db}")
      Database(globalParams.db.toString()).tryWith { db ->
         val pl = PersistenceLayer(db)
         if (pl.db.dbQueryUniqueStr("PRAGMA integrity_check").toLowerCase() != "ok") {
            logger.error("Integrity check of database failed! Exit program.")
            throw Exception("Integrity check of database failed! Exit program.")
         }
         db.dbExecNoResult("PRAGMA cache_size=-8000") // 8 MB
         db.dbExecNoResult("PRAGMA foreign_keys=ON")
         //db.dbExecNoResult("PRAGMA synchronous=1")

         PrepareDb.prepareDB(db) // has its own transaction management

         if (db.dbVersion == Global.programVersion) {
            if (Config.dryRun) {
               logger.info("-------- Dry run, no write changes are made to files ---------")
            }
            runBefore(pl)
            command.invoke(pl)
            runAfter(pl)
         } else {
            logger.error("The version of the database ${db.dbVersion} does not match the current program version (${Global.programVersion}). Please update the program.")
         }
      }
   }

   try {
      if (isHelpFallback(args)) { // offer some more options for showing help
         logger.info(parser.printout())
      } else {
         parser.parseArgs(args)
      }
   } catch (e: ArgParseException) {
      logger.info(parser.printout(e))
      if (isTest()) throw e // throw exception only in test case
   }
}

@Suppress("DuplicatedCode")
private fun createParser(outerCallback: (GlobalParams, (PersistenceLayer) -> Unit) -> Unit): ArgParser<GlobalParams> {
   return ArgParserBuilder(GlobalParams()).buildWith(ArgParserConfig(ignoreCase = true, noPrefixForActionParams = true)) {
      val globalParams = paramValues

      addActionParser("help", "Show all available options and commands") { logger.info(printout()) }
      add(paramValues::db, FileParam())
      add(Config::dryRun, BooleanParam())
      add(Config::verbose, BooleanParam())
      add(Config::headless, BooleanParam())
      add(Config::silent, BooleanParam())
      add(Config::allowMultithreading, BooleanParam())
      add(Config::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }))
      add(Config::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }))

      addActionParser(
            Commands.INDEX_FILES.command,
            ArgParserBuilder(IndexFilesParams()).buildWith {
               add(Config::fastMode, BooleanParam())
               add(Config::ignoreHashInFastMode, BooleanParam())
               add(Config::createHashOnlyForFirstMb, BooleanParam())
               add(Config::createThumbnails, BooleanParam())
               add(Config::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(paramValues::mediumDescription, StringParam())
               add(paramValues::mediumSerial, StringParam())
               add(paramValues::timeZone, StringParam())
               add(paramValues::noArchiveContents, BooleanParam())
               add(paramValues::updateHardlinksInLastIndex, BooleanParam())
               add(paramValues::lastIndexDir, FileParam(true))
               addNamelessLast(paramValues::dirs, FileListParam(1..10, true), "Directories to index", true)
            }) {
         val timeZone = if (paramValues.timeZone != null) TimeZone.getTimeZone(paramValues.timeZone) else null
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            if (!Config.headless) InfopanelSwing.show()
            try {
               for (dir in paramValues.dirs) {
                  IndexFiles(dir.canonicalFile,
                             paramValues.lastIndexDir?.canonicalFile,
                             paramValues.mediumDescription,
                             paramValues.mediumSerial,
                             timeZone,
                             !paramValues.noArchiveContents,
                             paramValues.updateHardlinksInLastIndex,
                             1,
                             2,
                             500_000,
                             pl).run()
               }
            } finally {
               InfopanelSwing.close()
            }
         }
      }

      addActionParser(
            Commands.SYNC_FILES.command,
            ArgParserBuilder(SyncFilesParams()).buildWith {
               add(Config::fastMode, BooleanParam())
               add(Config::ignoreHashInFastMode, BooleanParam())
               add(Config::createHashOnlyForFirstMb, BooleanParam())
               add(Config::createThumbnails, BooleanParam())
               add(Config::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(Config::minDiskFreeSpacePercent, IntParam())
               add(Config::minDiskFreeSpaceMB, IntParam())
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::timeZone, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), "Source directory", true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), "Target directory", true)
            }) {
         val timeZone = if (paramValues.timeZone != null) TimeZone.getTimeZone(paramValues.timeZone) else null
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            SyncFiles(pl).run(
                  paramValues.sourceDir!!.canonicalFile,
                  paramValues.targetDir!!.canonicalFile,
                  paramValues.mediumDescriptionSource,
                  paramValues.mediumDescriptionTarget,
                  paramValues.mediumSerialSource,
                  paramValues.mediumSerialTarget,
                  paramValues.skipIndexFilesOfSourceDir,
                  timeZone,
                  1,
                  2,
                  500_000)
         }
      }

      addActionParser(
            Commands.BACKUP_FILES.command,
            ArgParserBuilder(BackupFilesParams()).buildWith {
               add(Config::fastMode, BooleanParam())
               add(Config::ignoreHashInFastMode, BooleanParam())
               add(Config::createHashOnlyForFirstMb, BooleanParam())
               add(Config::maxChangedFilesWarningPercent, IntParam())
               add(Config::minAllowedChanges, IntParam())
               add(Config::createThumbnails, BooleanParam())
               add(Config::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(Config::minDiskFreeSpacePercent, IntParam())
               add(Config::minDiskFreeSpaceMB, IntParam())
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::timeZone, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               add(paramValues::indexArchiveContentsOfSourceDir, BooleanParam())
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), "Source directory", true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), "Target directory", true)
            }) {
         val timeZone = if (paramValues.timeZone != null) TimeZone.getTimeZone(paramValues.timeZone) else null
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            BackupFiles(pl).run(
                  paramValues.sourceDir!!.canonicalFile,
                  paramValues.targetDir!!.canonicalFile,
                  paramValues.mediumDescriptionSource,
                  paramValues.mediumDescriptionTarget,
                  paramValues.mediumSerialSource,
                  paramValues.mediumSerialTarget,
                  paramValues.indexArchiveContentsOfSourceDir,
                  paramValues.skipIndexFilesOfSourceDir,
                  timeZone,
                  1,
                  2,
                  500_000)
         }
      }

      addActionParser(
            Commands.VERIFY_FILES.command,
            ArgParserBuilder(VerifyFilesParams()).buildWith {
               add(Config::fastMode, BooleanParam())
               add(Config::ignoreHashInFastMode, BooleanParam())
               addNamelessLast(paramValues::dir, FileParam(checkIsDir = true), "Directory", true)
            },
            "Verify",
            {
               Config.fastMode = false // nur hier bei Verify per Default den Fastmode ausschalten
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            VerifyFiles(pl, true).run(paramValues.dir!!.canonicalFile)
         }
      }

      addActionParser(
            Commands.COMPARE_INDEX_RUNS.command,
            ArgParserBuilder(CompareIndexRunsParams()).buildWith {
               addNamelessLast(paramValues::indexNr1, IntParam(), "Directory 1", true)
               addNamelessLast(paramValues::indexNr2, IntParam(), "Directory 2", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            CompareIndexRuns(pl).run(paramValues.indexNr1.toLong(), paramValues.indexNr2.toLong())
         }
      }

      addActionParser(
            Commands.DELETE_DUPLICATE_FILES.command,
            ArgParserBuilder(DeleteDuplicateFilesParams()).buildWith {
               add(paramValues::deleteDuplicates, BooleanParam())
               add(paramValues::inclFilenameOnCompare, BooleanParam())
               add(paramValues::printOnlyDeleted, BooleanParam())
               add(paramValues::deletePathFilter, StringParam())
               addNamelessLast(paramValues::dirs, FileListParam(1..Int.MAX_VALUE, true), "Directories", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            DeleteDuplicateFiles(pl).run(
                  paramValues.dirs.map { it.canonicalFile },
                  paramValues.deleteDuplicates,
                  paramValues.deletePathFilter,
                  paramValues.inclFilenameOnCompare,
                  paramValues.printOnlyDeleted)
         }
      }

      addActionParser(
            Commands.FIND_FILES_WITH_NO_COPY.command,
            ArgParserBuilder(FindFilesWithNoCopyParams()).buildWith {
               add(paramValues::reverse, BooleanParam())
               addNamelessLast(paramValues::referenceDir, FileParam(true), "Reference directory", true)
               addNamelessLast(paramValues::toSearchInDirs, FileListParam(1..Int.MAX_VALUE, true), "Directories to search in", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            FindFilesWithNoCopy(pl).run(
                  paramValues.referenceDir!!.canonicalFile,
                  paramValues.toSearchInDirs.map { it.canonicalFile },
                  paramValues.reverse)
         }
      }

      addActionParser(
            Commands.CORRECT_DIFF_IN_FILE_MODIFICATION_DATE.command,
            ArgParserBuilder(CorrectDiffInFileModificationDateParams()).buildWith {
               add(paramValues::ignoreMilliseconds, BooleanParam())
               addNamelessLast(paramValues::referenceDir, FileParam(true), "Reference directory", true)
               addNamelessLast(paramValues::toSearchInDirs, FileListParam(1..Int.MAX_VALUE, true), "Directories to search in", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            CorrectDiffInFileModificationDate(pl).run(
                  paramValues.referenceDir!!.canonicalFile,
                  paramValues.toSearchInDirs.map { it.canonicalFile },
                  paramValues.ignoreMilliseconds)
         }
      }

      addActionParser(
            Commands.CORRECT_DIFF_IN_FILE_MODIFICATION_DATE_AND_EXIF_DATE_TAKEN.command,
            ArgParserBuilder(CorrectDiffInFileModificationDateAndExifDateTakenParams()).buildWith {
               add(paramValues::ignoreSecondsDiff, IntParam())
               add(paramValues::ignoreHoursDiff, IntParam())
               addNamelessLast(paramValues::dirs, FileListParam(1..Int.MAX_VALUE, true), "Directories to search in", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            CorrectDiffInFileModificationDateAndExifDateTaken(pl).run(
                  paramValues.dirs.map { it.canonicalFile },
                  paramValues.ignoreSecondsDiff,
                  paramValues.ignoreHoursDiff)
         }
      }

      addActionParser(
            Commands.RENAME_FILES_TO_MODIFICATION_DATE.command,
            ArgParserBuilder(RenameFilesToModificationDateParams()).buildWith {
               addNamelessLast(paramValues::dirs, FileListParam(1..Int.MAX_VALUE, true), "Directories", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            RenameFilesToModificationDate(pl).run(
                  paramValues.dirs.map { it.canonicalFile })
         }
      }

      addActionParser(
            Commands.LIST_INDEX_RUNS.command,
            ArgParserBuilder(ListIndexRunsParams()).buildWith {
               addNamelessLast(paramValues::dir, FileParam(), "Directory")
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            ListIndexRuns(pl).run(paramValues.dir?.canonicalFile)
         }
      }

      addActionParser(
            Commands.LIST_PATHS.command,
            ArgParserBuilder(ListPathsParams()).buildWith {
               addNamelessLast(paramValues::dir, FileParam(), "Directory", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            ListPaths(pl).run(paramValues.dir!!.canonicalFile)
         }
      }

      addActionParser(
            Commands.REMOVE_INDEX_RUN.command,
            ArgParserBuilder(RemoveIndexRunParams()).buildWith {
               addNamelessLast(paramValues::indexNr, IntParam())
               addNamelessLast(paramValues::indexNrRange, IntRangeParam())
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            when {
               paramValues.indexNr != null      -> {
                  RemoveIndexRun(pl).removeIndexRun(paramValues.indexNr!!.toLong())
               }
               paramValues.indexNrRange != null -> {
                  val result = pl.db.dbQuery(Queries.indexRun3, listOf(paramValues.indexNrRange!!.first, paramValues.indexNrRange!!.last)) {
                     it.getLong(1)
                  }
                  for (id in result) {
                     RemoveIndexRun(pl).removeIndexRun(id)
                  }
               }
               else                             -> {
                  logger.warn("a indexNr must be provided!")
               }
            }
         }
      }

      addActionParser(Commands.SHOW_DATABASE_REPORT.command) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            ShowDatabaseReport(pl).getOverview()
         }
      }

      addActionParser(
            Commands.IMPORT_OLD_DB.command,
            ArgParserBuilder(ImportOldDbParams()).buildWith {
               add(paramValues::mediumSerial, StringParam())
               add(paramValues::mediumDescription, StringParam())
               add(paramValues::oldDb, FileParam(checkIsFile = true), "Old DB", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            ImportOldDatabase(pl).import(paramValues.oldDb!!.canonicalFile, paramValues.mediumSerial, paramValues.mediumDescription)
         }
      }
   }
}

private fun isHelpFallback(args: Array<String>): Boolean {
   if (args.size == 1) {
      return args[0] in setOf("/h", "/help", "/?", "--?", "?")
   }
   return false
}

private fun isTest(): Boolean {
   return Exception().stackTrace.any { it.className.contains(".junit.") }
}

// todo support mapping of root paths, especially when comparing two indexRuns
// todo optimize memory usage
// todo optimize caching
