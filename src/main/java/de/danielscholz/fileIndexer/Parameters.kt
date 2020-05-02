package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.actions.IndexFiles
import de.danielscholz.kargparser.Description
import java.io.File
import java.util.*

class GlobalParams {
   var db: File? = null
}

class IndexFilesParams {
   @Description("Directories to index")
   var dirs: List<File> = listOf()
   var lastIndexDir: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
   var noArchiveContents: Boolean = false
   var updateHardlinksInLastIndex: Boolean = false
   var readConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class SyncFilesParams {
   @Description("Source directory")
   var sourceDir: File? = null

   @Description("Target directory")
   var targetDir: File? = null
   var mediumDescriptionSource: String? = null
   var mediumDescriptionTarget: String? = null
   var mediumSerialSource: String? = null
   var mediumSerialTarget: String? = null
   var skipIndexFilesOfSourceDir: Boolean = false
   var sourceReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
   var targetReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class BackupFilesParams {
   @Description("Source directory")
   var sourceDir: File? = null

   @Description("Target directory")
   var targetDir: File? = null
   var mediumDescriptionSource: String? = null
   var mediumDescriptionTarget: String? = null
   var mediumSerialSource: String? = null
   var mediumSerialTarget: String? = null
   var skipIndexFilesOfSourceDir = false
   var indexArchiveContentsOfSourceDir = false
   var sourceReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class DeleteDuplicateFilesParams {
   @Description("Directories")
   var dirs: List<File> = listOf()
   var inclFilenameOnCompare: Boolean = false
   var printOnlyDeleted: Boolean = false
   var deleteDuplicates: Boolean = false
   var deletePathFilter: String? = null
}

class FindFilesWithNoCopyParams {
   var referenceDir: File? = null

   @Description("Directories")
   var toSearchInDirs: List<File> = listOf()
   var reverse: Boolean = false
}

class CorrectDiffInFileModificationDateParams {
   @Description("Reference directory")
   var referenceDir: File? = null

   @Description("Directories to search in")
   var toSearchInDirs: List<File> = listOf()
   var ignoreMilliseconds: Boolean = false
}

class CorrectDiffInFileModificationDateAndExifDateTakenParams {
   @Description("Directories")
   var dirs: List<File> = listOf()
   var ignoreSecondsDiff: Int = 0
   var ignoreHoursDiff: Int = 0
}

class RenameFilesToModificationDateParams {
   @Description("Directories")
   var dirs: List<File> = listOf()
}

class ListIndexRunsParams {
   @Description("Directory")
   var dir: File? = null
}

class ListPathsParams {
   @Description("Directory")
   var dir: File? = null
}

class VerifyFilesParams {
   @Description("Directory")
   var dir: File? = null
}

class RemoveIndexRunParams {
   @Description("Index number")
   var indexNr: Int? = null
   @Description("Range of index numbers: start-end")
   var indexNrRange: IntRange? = null
}

class CompareIndexRunsParams {
   @Description("Index 1 number")
   var indexNr1: Int = 0

   @Description("Index 2 number")
   var indexNr2: Int = 0
}

class ImportOldDbParams {
   @Description("Old DB")
   var oldDb: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
}