package de.danielscholz.fileIndexer

import ch.qos.logback.classic.LoggerContext
import de.danielscholz.fileIndexer.actions.*
import de.danielscholz.fileIndexer.common.*
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
import java.io.Console
import java.io.File

/**
 * Information about this program can be found in README.md
 */

private val logger = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
   main(args, {}, {})
}

/**
 * @param args program arguments
 * @param runBefore Action that is called before the main program. Does not take place within a transaction!
 * @param runAfter Action that is called after the main program. Does not take place within a transaction!
 */
internal fun main(args: Array<String>, runBefore: (PersistenceLayer) -> Unit = {}, runAfter: (PersistenceLayer) -> Unit = {}) {
   registerShutdownCallback {
      Global.cancel = true
      (LoggerFactory.getILoggerFactory() as LoggerContext).stop()
   }
   registerLowMemoryListener()

   val parser = createParser(true) { globalParams: GlobalParams, command: (PersistenceLayer) -> Unit ->
      if (globalParams.db == null) globalParams.db = File("IndexedFiles")
      val inMemoryDb = globalParams.db!!.name == ":memory:"
      if (!inMemoryDb) {
         if (!globalParams.db!!.name.endsWith(".db")) {
            globalParams.db = File(globalParams.db.toString().ensureSuffix(".db")).canonicalFile
         } else {
            globalParams.db = globalParams.db!!.canonicalFile
         }
      }

      if (Config.INST.verbose) logger.debug("Database: ${globalParams.db}")
      Database(globalParams.db.toString()).tryWith { db ->
         val pl = PersistenceLayer(db)
         if (pl.db.dbQueryUniqueStr("PRAGMA integrity_check").toLowerCase() != "ok") {
            logger.error("Integrity check of database failed! Exit program.")
            throw Exception("Integrity check of database failed! Exit program.")
         }
         db.dbExecNoResult("PRAGMA cache_size=-8000") // 8 MB
         db.dbExecNoResult("PRAGMA foreign_keys=ON")
         //db.dbExecNoResult("PRAGMA synchronous=1")

         PrepareDb.run(db) // has its own transaction management

         if (db.dbVersion == Global.programVersion) {
            if (Config.INST.dryRun) {
               logger.info("-------- Dry run, no write changes are made to files ---------")
            }
            runBefore(pl)
            command(pl)
            runAfter(pl)
         } else {
            logger.error("The version of the database ${db.dbVersion} does not match the current program version (${Global.programVersion}). Please update the program.")
         }

         db.dbExecNoResult("PRAGMA optimize")
      }
   }

   try {
      if (!demandedHelp(args, parser)) {
         parser.parseArgs(args)
      }
   } catch (e: ArgParseException) {
      logger.info(parser.printout(e))
      if (isTest()) throw e // throw exception only in test case
   }
}

@Suppress("DuplicatedCode")
private fun createParser(toplevel: Boolean, outerCallback: (GlobalParams, (PersistenceLayer) -> Unit) -> Unit): ArgParser<GlobalParams> {
   return ArgParserBuilder(GlobalParams()).buildWith(ArgParserConfig(ignoreCase = true, noPrefixForActionParams = true)) {
      val globalParams = paramValues

      addActionParser("help", "Show all available options and commands") {
         logger.info(printout())
      }

      if (toplevel) {
         add(globalParams::db, FileParam())
      }
      add(Config.INST::dryRun, BooleanParam())
      add(Config.INST::verbose, BooleanParam())
      add(Config.INST::headless, BooleanParam())
      add(Config.INST::silent, BooleanParam())
      add(Config.INST::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }))
      add(Config.INST::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }))
      add(globalParams::timeZone, TimeZoneParam())

      if (toplevel) {
         addActionParser(Commands.CONSOLE.command) {
            val console = System.console()
            if (console == null) {
               logger.error("Console not supported!")
            } else {
               outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
                  processConsoleInputs(console, pl)
               }
            }
         }
      }

      addActionParser(
            Commands.INDEX_FILES.command,
            ArgParserBuilder(IndexFilesParams()).buildWith {
               add(Config.INST::fastMode, BooleanParam())
               add(Config.INST::ignoreHashInFastMode, BooleanParam())
               add(Config.INST::createHashOnlyForFirstMb, BooleanParam())
               add(Config.INST::createThumbnails, BooleanParam())
               add(Config.INST::thumbnailSize, IntParam())
               add(Config.INST::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(Config.INST::allowMultithreading, BooleanParam())
               add(Config.INST::maxThreads, IntParam())
               add(paramValues::mediumDescription, StringParam())
               add(paramValues::mediumSerial, StringParam())
               add(paramValues::noArchiveContents, BooleanParam())
               add(paramValues::updateHardlinksInLastIndex, BooleanParam())
               add(paramValues::lastIndexDir, FileParam())
               add(paramValues::readConfig, ReadConfigParam())
               addNamelessLast(paramValues::dirs, FileListParam(1..10, true), "Directories to index", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            if (!Config.INST.headless) InfopanelSwing.show()
            try {
               for (dir in paramValues.dirs) {
                  IndexFiles(dir.canonicalFile,
                             paramValues.lastIndexDir?.canonicalFile,
                             paramValues.mediumDescription,
                             paramValues.mediumSerial,
                             globalParams.timeZone,
                             !paramValues.noArchiveContents,
                             paramValues.updateHardlinksInLastIndex,
                             paramValues.readConfig,
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
               add(Config.INST::fastMode, BooleanParam())
               add(Config.INST::ignoreHashInFastMode, BooleanParam())
               add(Config.INST::createHashOnlyForFirstMb, BooleanParam())
               add(Config.INST::createThumbnails, BooleanParam())
               add(Config.INST::thumbnailSize, IntParam())
               add(Config.INST::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(Config.INST::allowMultithreading, BooleanParam())
               add(Config.INST::maxThreads, IntParam())
               add(Config.INST::minDiskFreeSpacePercent, IntParam())
               add(Config.INST::minDiskFreeSpaceMB, IntParam())
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               add(paramValues::sourceReadConfig, ReadConfigParam())
               add(paramValues::targetReadConfig, ReadConfigParam())
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), "Source directory", true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), "Target directory", true)
            }) {
         outerCallback.invoke(globalParams) { pl: PersistenceLayer ->
            SyncFiles(pl).run(
                  paramValues.sourceDir!!.canonicalFile,
                  paramValues.targetDir!!.canonicalFile,
                  paramValues.mediumDescriptionSource,
                  paramValues.mediumDescriptionTarget,
                  paramValues.mediumSerialSource,
                  paramValues.mediumSerialTarget,
                  paramValues.skipIndexFilesOfSourceDir,
                  globalParams.timeZone,
                  paramValues.sourceReadConfig,
                  paramValues.targetReadConfig)
         }
      }

      addActionParser(
            Commands.BACKUP_FILES.command,
            ArgParserBuilder(BackupFilesParams()).buildWith {
               add(Config.INST::fastMode, BooleanParam())
               add(Config.INST::ignoreHashInFastMode, BooleanParam())
               add(Config.INST::createHashOnlyForFirstMb, BooleanParam())
               add(Config.INST::createThumbnails, BooleanParam())
               add(Config.INST::thumbnailSize, IntParam())
               add(Config.INST::maxChangedFilesWarningPercent, IntParam())
               add(Config.INST::minAllowedChanges, IntParam())
               add(Config.INST::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam())
               add(Config.INST::allowMultithreading, BooleanParam())
               add(Config.INST::maxThreads, IntParam())
               add(Config.INST::minDiskFreeSpacePercent, IntParam())
               add(Config.INST::minDiskFreeSpaceMB, IntParam())
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               add(paramValues::indexArchiveContentsOfSourceDir, BooleanParam())
               add(paramValues::sourceReadConfig, ReadConfigParam())
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), "Source directory", true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), "Target directory", true)
            }) {
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
                  globalParams.timeZone,
                  paramValues.sourceReadConfig)
         }
      }

      addActionParser(
            Commands.VERIFY_FILES.command,
            ArgParserBuilder(VerifyFilesParams()).buildWith {
               add(Config.INST::fastMode, BooleanParam())
               add(Config.INST::ignoreHashInFastMode, BooleanParam())
               addNamelessLast(paramValues::dir, FileParam(checkIsDir = true), "Directory", true)
            },
            "Verify",
            {
               Config.INST.fastMode = false // deactivate fastMode only on verify as default
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

      addActionParser(Commands.STATUS.command) {
         logger.info("verbose = ${Config.INST.verbose}")
         logger.info("silent = ${Config.INST.silent}")
         logger.info("headless = ${Config.INST.headless}")
         logger.info("dryRun = ${Config.INST.dryRun}")
         logger.info("fastMode = ${Config.INST.fastMode}")
         logger.info("ignoreHashInFastMode = ${Config.INST.ignoreHashInFastMode}")
         logger.info("excludedFiles = ${Config.INST.excludedFiles}")
         logger.info("excludedPaths = ${Config.INST.excludedPaths}")
         logger.info("defaultExcludedFiles = ${Config.INST.defaultExcludedFiles}")
         logger.info("defaultExcludedPaths = ${Config.INST.defaultExcludedPaths}")
         logger.info("allowMultithreading = ${Config.INST.allowMultithreading}")
         logger.info("createThumbnails = ${Config.INST.createThumbnails}")
         logger.info("maxChangedFilesWarningPercent = ${Config.INST.maxChangedFilesWarningPercent}")
         logger.info("minAllowedChanges = ${Config.INST.minAllowedChanges}")
         logger.info("minDiskFreeSpacePercent = ${Config.INST.minDiskFreeSpacePercent}")
         logger.info("minDiskFreeSpaceMB = ${Config.INST.minDiskFreeSpaceMB}")
      }

      if (!toplevel) {
         addActionParser(Commands.EXIT.command) {
            Global.cancel = true
         }
      }
   }
}

private fun demandedHelp(args: Array<String>, parser: ArgParser<GlobalParams>): Boolean {
   // offer some more options for showing help and to get help for a specific command
   val helpArguments = setOf("/?", "--?", "?", "--help")
   var foundIdx = -1
   if (args.anyIndexed { idx, arg ->
            if (arg in helpArguments) {
               foundIdx = idx
               true
            } else false
         }) {
      val argumentsWithoutHelp = args.toMutableList()
      argumentsWithoutHelp.removeAt(foundIdx)
      logger.info(parser.printout(argumentsWithoutHelp.toTypedArray(), false))
      return true
   }
   return false
}

private fun processConsoleInputs(console: Console, pl: PersistenceLayer) {
   while (true) {
      if (Global.cancel) break
      print("> ")
      val line = console.readLine()
      if (Global.cancel) break
      val arguments = parseCmd(line)
      if (arguments.isEmpty()) {
         continue
      } else {
         var commandProcessed = false
         val parser = createParser(false) { globalParams: GlobalParams, command: (PersistenceLayer) -> Unit ->
            try {
               command(pl)
               commandProcessed = true
            } catch (e: Exception) {
               e.printStackTrace()
               pl.db.rollback()
            }
         }
         try {
            val args = arguments.toTypedArray()
            if (!demandedHelp(args, parser)) {
               val configCopy = Config.INST.getCopy()

               parser.parseArgs(args)

               // restore settings if a command like "index", "sync" was processed
               // if only settings were changed (not in combination with a command), keep them
               if (commandProcessed) {
                  Config.INST = configCopy
               } else {
                  val changed = Config.INST.getDiffTo(configCopy).map { "${it.first} = ${it.second}" }.joinToString("\n")
                  if (changed.isNotEmpty()) {
                     logger.info(changed)
                  }
               }
            }
         } catch (e: ArgParseException) {
            logger.info(parser.printout(e))
         }
      }
   }
}

// todo auto_vacuum
// todo support mapping of root paths, especially when comparing two indexRuns
// todo optimize memory usage
// todo optimize caching
