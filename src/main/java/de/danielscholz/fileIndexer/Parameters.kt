package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.actions.IndexFiles
import java.io.File
import java.util.*

class GlobalParams {
   var db: File? = null
   var timeZone: TimeZone? = TimeZone.getDefault()
}

class IndexFilesParams {
   var dirs: List<File> = listOf()
   var lastIndexDir: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
   var noArchiveContents: Boolean = false
   var updateHardlinksInLastIndex: Boolean = false
   var readConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class SyncFilesParams {
   var sourceDir: File? = null
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
   var sourceDir: File? = null
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
   var dirs: List<File> = listOf()
   var inclFilenameOnCompare: Boolean = false
   var printOnlyDeleted: Boolean = false
   var deleteDuplicates: Boolean = false
   var deletePathFilter: String? = null
}

class FindFilesWithNoCopyParams {
   var referenceDir: File? = null
   var toSearchInDirs: List<File> = listOf()
   var reverse: Boolean = false
}

class CorrectDiffInFileModificationDateParams {
   var referenceDir: File? = null
   var toSearchInDirs: List<File> = listOf()
   var ignoreMilliseconds: Boolean = false
}

class CorrectDiffInFileModificationDateAndExifDateTakenParams {
   var dirs: List<File> = listOf()
   var ignoreSecondsDiff: Int = 0
   var ignoreHoursDiff: Int = 0
}

class RenameFilesToModificationDateParams {
   var dirs: List<File> = listOf()
}

class ListIndexRunsParams {
   var dir: File? = null
}

class ListPathsParams {
   var dir: File? = null
}

class VerifyFilesParams {
   var dir: File? = null
}

class RemoveIndexRunParams {
   var indexNr: Int? = null
   var indexNrRange: IntRange? = null
}

class CompareIndexRunsParams {
   var indexNr1: Int = 0
   var indexNr2: Int = 0
}

class ImportOldDbParams {
   var oldDb: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
}