package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.actions.*
import de.danielscholz.fileIndexer.common.*
import de.danielscholz.fileIndexer.persistence.FileLocation
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
import java.util.*
import kotlin.reflect.KProperty0

/**
 * Information about this program can be found in README.md
 */

private val logger = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
   registerShutdownCallback {
      Global.cancel = true
      (LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext).stop()
   }
   registerLowMemoryListener()

   main(args, true)
}

/**
 * @param args program arguments
 * @param runBeforeCmd Action that is called before the main program. Does not take place within a transaction!
 * @param runAfterCmd Action that is called after the main program. Does not take place within a transaction!
 */
internal fun main(args: Array<String>,
                  toplevel: Boolean = true,
                  parentGlobalParams: GlobalParams? = null,
                  runBeforeArgParsing: () -> Unit = {},
                  runAfterArgParsing: () -> Unit = {},
                  runBeforeCmd: (PersistenceLayer) -> Unit = {},
                  runAfterCmd: (PersistenceLayer) -> Unit = {}) {

   val argsSplittetOnPipes = args.splitPipes() // support for pipeline processing

   if (argsSplittetOnPipes.isEmpty() || argsSplittetOnPipes[0].isEmpty()) {
      logger.error("No arguments are provided!")
      return
   }

   val globalParams = parentGlobalParams ?: GlobalParams()
   var lastHadResult: Boolean
   val parserWithNoCommandProcessing = createParser(toplevel, globalParams) { hasResult: Boolean, _ -> lastHadResult = hasResult }

   if (!demandedHelp(argsSplittetOnPipes[0], parserWithNoCommandProcessing)) {
      runBeforeArgParsing()

      try {
         // test all program arguments with a parser doing no command (globalParams are set!)
         argsSplittetOnPipes.forEachIndexed { index, argsSlice ->
            lastHadResult = false
            parserWithNoCommandProcessing.parseArgs(argsSlice)
            if (!lastHadResult && index < argsSplittetOnPipes.lastIndex) {
               logger.error("Pipeline is broken: ${argsSlice.joinToString(" ")} has no result!")
               return
            }
         }

         openDatabaseAndRunCommands(globalParams.db) { pl: PersistenceLayer ->

            var commandResult: List<FileLocation>? = null

            // create parser which executes commands. This parser should not throw a ArgParseException.
            val parser = createParser(toplevel, globalParams) { hasResult: Boolean, command: (PersistenceLayer, List<FileLocation>?, Boolean) -> List<FileLocation>? ->
               setRootLoggerLevel()

               if (Config.INST.dryRun) {
                  logger.info("-------- Dry run, no write changes are made to files ---------")
               }
               runBeforeCmd(pl)
               commandResult = command(pl, commandResult, argsSplittetOnPipes.size > 1)
               runAfterCmd(pl)
            }

            // all pipeline commands are executed within the same database connection
            argsSplittetOnPipes.forEach {
               parser.parseArgs(it)
            }
         }
      } catch (e: CancelPipelineException) {
         logger.error("Pipeline execution canceled")
         if (isTest()) throw e // throw exception only in test case
      } catch (e: ArgParseException) {
         logger.info(parserWithNoCommandProcessing.printout(e))
         if (isTest()) throw e // throw exception only in test case
      }

      runAfterArgParsing()
   }
}

private fun openDatabaseAndRunCommands(dbFile_: File?, commands: (pl: PersistenceLayer) -> Unit) {
   var dbFile = dbFile_
   if (dbFile == null) dbFile = File("IndexedFiles")
   val inMemoryDb = dbFile.name == ":memory:"
   if (!inMemoryDb) {
      if (!dbFile.name.endsWith(".db")) {
         dbFile = File(dbFile.toString().ensureSuffix(".db")).canonicalFile
      } else {
         dbFile = dbFile.canonicalFile
      }
   }

   logger.debug("Database: $dbFile")
   Database(dbFile.toString()).tryWith { db ->
      val pl = PersistenceLayer(db)
      logger.info("Perform database integrity check")
      if (pl.db.dbQueryUniqueStr("PRAGMA integrity_check").toLowerCase() != "ok") {
         logger.error("ERROR: Integrity check of database failed! Exit program.")
         throw Exception("Integrity check of database failed! Exit program.")
      }
      db.dbExecNoResult("PRAGMA cache_size=-8000") // 8 MB
      db.dbExecNoResult("PRAGMA foreign_keys=ON")
      //db.dbExecNoResult("PRAGMA synchronous=1")

      PrepareDb.run(db) // has its own transaction management

      if (db.dbVersion == Global.programVersion) {
         commands(pl)
      } else {
         logger.error("ERROR: The version of the database ${db.dbVersion} does not match the current program version (${Global.programVersion}). Please update the program.")
      }

      db.dbExecNoResult("PRAGMA optimize")
   }
}

@Suppress("DuplicatedCode")
private fun createParser(toplevel: Boolean,
                         globalParams: GlobalParams,
                         outerCallback: (Boolean, (PersistenceLayer, List<FileLocation>?, Boolean) -> List<FileLocation>?) -> Unit): ArgParser<GlobalParams> {

   fun ArgParserBuilder<*>.addConfigParamsForIndexFiles() {
      add(Config.INST::fastMode, BooleanParam())
      add(Config.INST::ignoreHashInFastMode, BooleanParam())
      add(Config.INST::createHashOnlyForFirstMb, BooleanParam())
      add(Config.INST::createThumbnails, BooleanParam())
      add(Config.INST::thumbnailSize, IntParam())
      add(Config.INST::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam(typeDescription = ""))
      add(Config.INST::allowMultithreading, BooleanParam())
      add(Config.INST::maxThreads, IntParam())
   }

   fun ArgParserBuilder<*>.addConfigParamsForSyncOrBackup() {
      add(Config.INST::maxChangedFilesWarningPercent, IntParam())
      add(Config.INST::minAllowedChanges, IntParam())
      add(Config.INST::minDiskFreeSpacePercent, IntParam())
      add(Config.INST::minDiskFreeSpaceMB, IntParam())
   }

   fun loggerInfo(property: KProperty0<*>) {
      loggerInfo(property.name, property.get())
   }

   return ArgParserBuilder(globalParams).buildWith(ArgParserConfig(ignoreCase = true, noPrefixForActionParams = true)) {

      addActionParser("help", "Show all available options and commands") {
         logger.info(printout())
      }

      add(globalParams::db, FileParam())
      add(Config.INST::dryRun, BooleanParam())
      add(Config.INST::verbose, BooleanParam())
      add(Config.INST::logLevel, StringParam())
      add(Config.INST::progressWindow, BooleanParam())
      add(Config.INST::confirmations, BooleanParam())
      add(Config.INST::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
      add(Config.INST::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
      add(Config.INST::timeZone, TimeZoneParam())

      if (toplevel) {
         addActionParser(Commands.CONSOLE.command) {
            val console = System.console()
            if (console == null) {
               logger.error("ERROR: Console not supported!")
            } else {
               // do not call outerCallback, because we don't want to open a database yet
               processConsoleInputs(console, globalParams)
            }
         }
      }

      addActionParser(
            Commands.INDEX_FILES.command,
            ArgParserBuilder(IndexFilesParams()).buildWith {
               addConfigParamsForIndexFiles()
               add(paramValues::mediumDescription, StringParam())
               add(paramValues::mediumSerial, StringParam())
               add(paramValues::indexArchiveContents, BooleanParam())
               add(paramValues::updateHardlinksInLastIndex, BooleanParam())
               add(paramValues::lastIndexDir, FileParam())
               add(paramValues::readConfig, ReadConfigParam())
               add(paramValues::includedPaths, StringListParam(mapper = { it.replace('\\', '/').removePrefix("/").removeSuffix("/") }))
               addNamelessLast(paramValues::dir, FileParam(true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            IndexFiles(paramValues.dir!!.canonicalFile,
                       paramValues.includedPaths,
                       paramValues.lastIndexDir?.canonicalFile,
                       paramValues.mediumDescription,
                       paramValues.mediumSerial,
                       paramValues.indexArchiveContents,
                       paramValues.updateHardlinksInLastIndex,
                       paramValues.readConfig,
                       pl).run()
            null
         }
      }

      addActionParser(
            Commands.SYNC_FILES.command,
            ArgParserBuilder(SyncFilesParams()).buildWith {
               addConfigParamsForIndexFiles()
               addConfigParamsForSyncOrBackup()
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               add(paramValues::sourceReadConfig, ReadConfigParam())
               add(paramValues::targetReadConfig, ReadConfigParam())
               add(paramValues::includedPaths, StringListParam(mapper = { it.replace('\\', '/').removePrefix("/").removeSuffix("/") }))
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            SyncFiles(pl).run(
                  paramValues.sourceDir!!.canonicalFile,
                  paramValues.targetDir!!.canonicalFile,
                  paramValues.includedPaths,
                  paramValues.mediumDescriptionSource,
                  paramValues.mediumDescriptionTarget,
                  paramValues.mediumSerialSource,
                  paramValues.mediumSerialTarget,
                  paramValues.skipIndexFilesOfSourceDir,
                  paramValues.indexArchiveContentsOfSourceDir,
                  paramValues.sourceReadConfig,
                  paramValues.targetReadConfig)
            null
         }
      }

      addActionParser(
            Commands.BACKUP_FILES.command,
            ArgParserBuilder(BackupFilesParams()).buildWith {
               addConfigParamsForIndexFiles()
               addConfigParamsForSyncOrBackup()
               add(paramValues::mediumDescriptionSource, StringParam())
               add(paramValues::mediumDescriptionTarget, StringParam())
               add(paramValues::mediumSerialSource, StringParam())
               add(paramValues::mediumSerialTarget, StringParam())
               add(paramValues::skipIndexFilesOfSourceDir, BooleanParam())
               add(paramValues::indexArchiveContentsOfSourceDir, BooleanParam())
               add(paramValues::sourceReadConfig, ReadConfigParam())
               add(paramValues::includedPaths, StringListParam(mapper = { it.replace('\\', '/').removePrefix("/").removeSuffix("/") }))
               addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
               addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            BackupFiles(pl).run(
                  paramValues.sourceDir!!.canonicalFile,
                  paramValues.targetDir!!.canonicalFile,
                  paramValues.includedPaths,
                  paramValues.mediumDescriptionSource,
                  paramValues.mediumDescriptionTarget,
                  paramValues.mediumSerialSource,
                  paramValues.mediumSerialTarget,
                  paramValues.indexArchiveContentsOfSourceDir,
                  paramValues.skipIndexFilesOfSourceDir,
                  paramValues.sourceReadConfig)
            null
         }
      }

      addActionParser(
            Commands.VERIFY_FILES.command,
            ArgParserBuilder(VerifyFilesParams()).buildWith {
               add(Config.INST::fastMode, BooleanParam())
               add(Config.INST::ignoreHashInFastMode, BooleanParam())
               addNamelessLast(paramValues::dir, FileParam(checkIsDir = true), required = true)
            },
            "Verify",
            {
               Config.INST.fastMode = false // deactivate fastMode only on verify as default
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            VerifyFiles(pl, true).run(paramValues.dir!!.canonicalFile)
            null
         }
      }

      addActionParser(
            Commands.COMPARE_INDEX_RUNS.command,
            ArgParserBuilder(CompareIndexRunsParams()).buildWith {
               addNamelessLast(paramValues::indexNr1, IntParam(), required = true)
               addNamelessLast(paramValues::indexNr2, IntParam(), required = true)
               addNamelessLast(paramValues::result, EnumParam(CompareIndexRunsParams.CompareIndexRunsResult::class.java))
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            CompareIndexRuns(pl).run(paramValues.indexNr1.toLong(), paramValues.indexNr2.toLong(), paramValues.result)
         }
      }

      addActionParser(
            Commands.FIND_DUPLICATE_FILES.command,
            ArgParserBuilder(DeleteDuplicateFilesParams()).buildWith {
               add(paramValues::inclFilenameOnCompare, BooleanParam())
               addNamelessLast(paramValues::referenceDir, FileParam(true), required = true)
               addNamelessLast(paramValues::toSearchInDirs, FileListParam(1..Int.MAX_VALUE, true), required = true)
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            FindDuplicateFiles(pl).run(
                  paramValues.referenceDir!!.canonicalFile,
                  paramValues.toSearchInDirs.map { it.canonicalFile },
                  paramValues.inclFilenameOnCompare)
         }
      }

      addActionParser(
            Commands.FIND_FILES_WITH_NO_COPY.command,
            ArgParserBuilder(FindFilesWithNoCopyParams()).buildWith {
               add(paramValues::reverse, BooleanParam())
               addNamelessLast(paramValues::referenceDir, FileParam(true), required = true)
               addNamelessLast(paramValues::toSearchInDirs, FileListParam(1..Int.MAX_VALUE, true), required = true)
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
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
               addNamelessLast(paramValues::referenceDir, FileParam(true), required = true)
               addNamelessLast(paramValues::toSearchInDirs, FileListParam(1..Int.MAX_VALUE, true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            CorrectDiffInFileModificationDate(pl).run(
                  paramValues.referenceDir!!.canonicalFile,
                  paramValues.toSearchInDirs.map { it.canonicalFile },
                  paramValues.ignoreMilliseconds)
            null
         }
      }

      addActionParser(
            Commands.CORRECT_DIFF_IN_FILE_MODIFICATION_DATE_AND_EXIF_DATE_TAKEN.command,
            ArgParserBuilder(CorrectDiffInFileModificationDateAndExifDateTakenParams()).buildWith {
               add(paramValues::ignoreSecondsDiff, IntParam())
               add(paramValues::ignoreHoursDiff, IntParam())
               addNamelessLast(paramValues::dirs, FileListParam(1..Int.MAX_VALUE, true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            CorrectDiffInFileModificationDateAndExifDateTaken(pl).run(
                  paramValues.dirs.map { it.canonicalFile },
                  paramValues.ignoreSecondsDiff,
                  paramValues.ignoreHoursDiff)
            null
         }
      }

      addActionParser(
            Commands.RENAME_FILES_TO_MODIFICATION_DATE.command,
            ArgParserBuilder(RenameFilesToModificationDateParams()).buildWith {
               addNamelessLast(paramValues::dirs, FileListParam(1..Int.MAX_VALUE, true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            RenameFilesToModificationDate(pl).run(
                  paramValues.dirs.map { it.canonicalFile })
            null
         }
      }

      addActionParser(
            Commands.LIST_INDEX_RUNS.command,
            ArgParserBuilder(ListIndexRunsParams()).buildWith {
               addNamelessLast(paramValues::dir, FileParam())
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            ListIndexRuns(pl).run(paramValues.dir?.canonicalFile)
            null
         }
      }

      addActionParser(
            Commands.LIST_PATHS.command,
            ArgParserBuilder(ListPathsParams()).buildWith {
               addNamelessLast(paramValues::dir, FileParam(), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            ListPaths(pl).run(paramValues.dir!!.canonicalFile)
            null
         }
      }

      addActionParser(
            Commands.REMOVE_INDEX_RUN.command,
            ArgParserBuilder(RemoveIndexRunParams()).buildWith {
               addNamelessLast(paramValues::indexNr, IntParam())
               addNamelessLast(paramValues::indexNrRange, IntRangeParam())
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
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
            null
         }
      }

      addActionParser(Commands.SHOW_DATABASE_REPORT.command) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            ShowDatabaseReport(pl).getOverview()
            null
         }
      }

      addActionParser(
            Commands.IMPORT_OLD_DB.command,
            ArgParserBuilder(ImportOldDbParams()).buildWith {
               add(paramValues::mediumSerial, StringParam())
               add(paramValues::mediumDescription, StringParam())
               add(paramValues::oldDb, FileParam(checkIsFile = true), required = true)
            }) {
         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            ImportOldDatabase(pl, paramValues.oldDb!!.canonicalFile, paramValues.mediumSerial, paramValues.mediumDescription).import()
            null
         }
      }

      addActionParser(Commands.STATUS.command) {
         loggerInfo(globalParams::db)
         loggerInfo(Config.INST::timeZone)
         loggerInfo(Config.INST::verbose)
         loggerInfo(Config.INST::confirmations)
         loggerInfo(Config.INST::progressWindow)
         loggerInfo(Config.INST::dryRun)
         loggerInfo(Config.INST::fastMode)
         loggerInfo(Config.INST::ignoreHashInFastMode)
         loggerInfo(Config.INST::alwaysCheckHashOnIndexForFilesSuffix)
         loggerInfo(Config.INST::excludedFiles)
         loggerInfo(Config.INST::excludedPaths)
         loggerInfo(Config.INST::defaultExcludedFiles)
         loggerInfo(Config.INST::defaultExcludedPaths)
         loggerInfo(Config.INST::allowMultithreading)
         loggerInfo(Config.INST::createThumbnails)
         loggerInfo(Config.INST::thumbnailSize)
         loggerInfo(Config.INST::maxChangedFilesWarningPercent)
         loggerInfo(Config.INST::minAllowedChanges)
         loggerInfo(Config.INST::minDiskFreeSpacePercent)
         loggerInfo(Config.INST::minDiskFreeSpaceMB)
      }

      if (!toplevel) {
         addActionParser(Commands.EXIT.command) {
            Global.cancel = true
         }
      }

      addHeadline("\nThe following commands are for pipelined executions:\n")

      addActionParser(
            Commands.FILTER.command,
            ArgParserBuilder(FilterFilesParams()).buildWith {
               add(paramValues::isJavaRegex, BooleanParam())
               add(paramValues::path, StringParam())
               add(paramValues::file, StringParam())
               add(paramValues::minSize, FileSizeParam())
               add(paramValues::maxSize, FileSizeParam())
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            if (pipelineResult != null) {
               return@invoke FilterFiles().run(pipelineResult,
                                               paramValues.path,
                                               paramValues.file,
                                               paramValues.isJavaRegex,
                                               paramValues.minSize,
                                               paramValues.maxSize)
            }
            null
         }
      }

      addActionParser(
            Commands.DELETE.command,
            ArgParserBuilder(DeleteFilesParams()).buildWith {
               add(paramValues::deleteEmptyDirs, BooleanParam())
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            if (pipelineResult != null) {
               DeleteFiles().run(pipelineResult, paramValues.deleteEmptyDirs)
            }
            pipelineResult
         }
      }

      addActionParser(
            Commands.MOVE.command,
            ArgParserBuilder(MoveFilesParams()).buildWith {
               add(paramValues::basePath, FileParam(), required = true)
               add(paramValues::toDir, FileParam(), required = true)
               add(paramValues::deleteEmptyDirs, BooleanParam())
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            if (pipelineResult != null) {
               MoveFiles().run(pipelineResult, paramValues.basePath!!, paramValues.toDir!!, paramValues.deleteEmptyDirs)
            }
            pipelineResult
         }
      }

      addActionParser(
            Commands.PRINT.command,
            ArgParserBuilder(PrintFilesParams()).buildWith {
               add(paramValues::folderOnly, BooleanParam())
               add(paramValues::details, BooleanParam())
            }) {
         outerCallback.invoke(true) { pl: PersistenceLayer, pipelineResult: List<FileLocation>?, provideResult: Boolean ->
            if (pipelineResult != null) {
               PrintFiles().run(pipelineResult, paramValues.folderOnly, paramValues.details)
            }
            pipelineResult
         }
      }
   }
}

private fun processConsoleInputs(console: Console, globalParams: GlobalParams) {
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
         var configCopy = Config.INST // do not create a copy yet; it is to early
         var globalParamsCopy = globalParams

         main(arguments.toTypedArray(),
              false,
              globalParams,
              {
                 configCopy = Config.INST.getCopy()
                 globalParamsCopy = globalParams.getCopy()
              },
              {
                 // restore settings if a command like "index", "sync" was processed
                 // if only settings were changed (not in combination with a command), keep them
                 if (commandProcessed) {
                    copyProperties(globalParamsCopy, globalParams)
                    copyProperties(configCopy, Config.INST)
                 } else {
                    globalParams.getDiffTo(globalParamsCopy).forEach { loggerInfo(it.first, it.second) }
                    Config.INST.getDiffTo(configCopy).forEach { loggerInfo(it.first, it.second) }
                 }
              },
              {},
              { commandProcessed = true }
         )
      }
   }
}

private fun demandedHelp(args: Array<String>, parser: ArgParser<GlobalParams>): Boolean {
   // offer some more options for showing help and to get help for a specific command
   val helpArguments = setOf("/?", "--?", "?", "--help", "help")
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

private fun Array<String>.splitPipes(): List<Array<String>> {
   var args = this
   val result = mutableListOf<Array<String>>()
   while (args.contains("|")) {
      val indexOf = args.indexOf("|")
      if (indexOf >= 0) {
         if (indexOf > 0) {
            val copy1 = this.copyOfRange(0, indexOf)
            result.add(copy1)
         }
         args = args.copyOfRange(indexOf + 1, args.size)
      } else throw IllegalStateException()
   }
   if (args.isNotEmpty()) result.add(args)
   return result
}

private fun loggerInfo(propertyName: String, propertyValue: Any?) {
   fun convertSingle(value: Any?): String? {
      if (value is String) return "\"$value\""
      if (value is Boolean) return BooleanParam().convertToStr(value)
      if (value is File) return FileParam().convertToStr(value)
      if (value is TimeZone) return TimeZoneParam().convertToStr(value)
      if (value is IntRange) return IntRangeParam().convertToStr(value)
      if (value is IndexFiles.ReadConfig) return ReadConfigParam().convertToStr(value)
      return if (value != null) value.toString() else ""
   }

   var value: Any? = propertyValue
   if (value is Collection<*>) {
      value = value.joinToString(transform = { convertSingle(it).toString() })
   } else {
      value = convertSingle(value)
   }
   logger.info("${propertyName} = $value")
}

// todo auto_vacuum
// todo support mapping of root paths, especially when comparing two indexRuns
// todo optimize memory usage
// todo optimize caching
// todo Feature for backups: How many space is used by a backup run? (list for all backups)
