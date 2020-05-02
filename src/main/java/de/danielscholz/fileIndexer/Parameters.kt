package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.actions.IndexFiles
import de.danielscholz.kargparser.Description
import java.io.File
import java.util.*

class GlobalParams {
   @Description("Database file")
   var db: File? = null
}

class IndexFilesParams {
   @Description("Directories to index")
   var dirs: List<File> = listOf()

   @Description("Last indexed directory, leave empty for most use cases!")
   var lastIndexDir: File? = null

   @Description("Description of the medium, like 'Backup CD 1'")
   var mediumDescription: String? = null

   @Description("Serial number of the medium, will be determined automatically if running under windows")
   var mediumSerial: String? = null

   @Description("Also create an index of archive contents")
   var indexArchiveContents: Boolean = true

   @Description("Update hardlink attribute in last index layer; leave as is in most use cases")
   var updateHardlinksInLastIndex: Boolean = false

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
   var readConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class SyncFilesParams {
   @Description("Source directory")
   var sourceDir: File? = null

   @Description("Target directory")
   var targetDir: File? = null

   @Description("Description of the source medium, like 'Drive C'")
   var mediumDescriptionSource: String? = null

   @Description("Description of the target medium, like 'Backup HD 1'")
   var mediumDescriptionTarget: String? = null

   @Description("Serial number of the source medium, will be determined automatically if running under windows")
   var mediumSerialSource: String? = null

   @Description("Serial number of the target medium, will be determined automatically if running under windows")
   var mediumSerialTarget: String? = null

   @Description("Also create an index of archive contents")
   var indexArchiveContentsOfSourceDir = false

   @Description("Skip create index of source directory. For most applications please do not change this!")
   var skipIndexFilesOfSourceDir: Boolean = false

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
   var sourceReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
   var targetReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class BackupFilesParams {
   @Description("Source directory")
   var sourceDir: File? = null

   @Description("Target directory")
   var targetDir: File? = null

   @Description("Description of the source medium, like 'Drive C'")
   var mediumDescriptionSource: String? = null

   @Description("Description of the target medium, like 'Backup HD 1'")
   var mediumDescriptionTarget: String? = null

   @Description("Serial number of the source medium, will be determined automatically if running under windows")
   var mediumSerialSource: String? = null

   @Description("Serial number of the target medium, will be determined automatically if running under windows")
   var mediumSerialTarget: String? = null

   @Description("Also create an index of archive contents")
   var indexArchiveContentsOfSourceDir = false

   @Description("Skip create index of source directory. For most applications please do not change this!")
   var skipIndexFilesOfSourceDir = false

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
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