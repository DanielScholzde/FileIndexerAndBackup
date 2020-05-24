package de.danielscholz.fileIndexer

import de.danielscholz.fileIndexer.actions.IndexFiles
import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.kargparser.Description
import java.io.File

class GlobalParams {
   @Description("Database file")
   var db: File? = null
}

class IndexFilesParams {
   @Description("Directory to index")
   var dir: MyPath? = null

   @Description("Included directories; base path is 'dir'. Separator char is \"/\". Specify only if needed")
   var includedPaths: List<String> = listOf()

   @Description("Last indexed directory, leave empty for most use cases!")
   var lastIndexDir: MyPath? = null

   @Description("Description of the medium, like 'Backup CD 1'")
   var mediumDescription: String? = null

   @Description("Also create an index of archive contents")
   var indexArchiveContents: Boolean = true

   @Description("Update hardlink attribute in last index layer; leave as is in most use cases")
   var updateHardlinksInLastIndex: Boolean = false

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
   var readConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class SyncFilesParams {
   @Description("Source directory")
   var sourceDir: MyPath? = null

   @Description("Target directory")
   var targetDir: MyPath? = null

   @Description("Included directories; base path is 'sourceDir'. Separator char is \"/\". Specify only if needed")
   var includedPaths: List<String> = listOf()

   @Description("Description of the source medium, like 'Drive C'")
   var mediumDescriptionSource: String? = null

   @Description("Description of the target medium, like 'Backup HD 1'")
   var mediumDescriptionTarget: String? = null

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
   var sourceDir: MyPath? = null

   @Description("Target directory")
   var targetDir: MyPath? = null

   @Description("Included directories; base path is 'sourceDir'. Separator char is \"/\". Specify only if needed")
   var includedPaths: List<String> = listOf()

   @Description("Description of the source medium, like 'Drive C'")
   var mediumDescriptionSource: String? = null

   @Description("Description of the target medium, like 'Backup HD 1'")
   var mediumDescriptionTarget: String? = null

   @Description("Also create an index of archive contents")
   var indexArchiveContentsOfSourceDir = false

   @Description("Skip create index of source directory. For most applications please do not change this!")
   var skipIndexFilesOfSourceDir = false

   @Description("Optimize read speed for different types of medium (number of parallel reads)")
   var sourceReadConfig: IndexFiles.ReadConfig = IndexFiles.ReadConfig.hd
}

class DeleteDuplicateFilesParams {
   @Description("Reference Directory")
   var referenceDir: MyPath? = null

   @Description("Directories to search for duplicates")
   var toSearchInDirs: List<MyPath> = listOf()
   var inclFilenameOnCompare: Boolean = false
}

class FindFilesWithNoCopyParams {
   @Description("Reference directory")
   var referenceDir: MyPath? = null

   @Description("Directories to search in")
   var toSearchInDirs: List<MyPath> = listOf()

   @Description("Reverse the meaning of referenceDir and toSearchInDirs")
   var reverse: Boolean = false
}

class CorrectDiffInFileModificationDateParams {
   @Description("Reference directory")
   var referenceDir: MyPath? = null

   @Description("Directories to search in")
   var toSearchInDirs: List<MyPath> = listOf()

   @Description("Ignore milliseconds when comparing modification dates")
   var ignoreMilliseconds: Boolean = false
}

class CorrectDiffInFileModificationDateAndExifDateTakenParams {
   @Description("Directories")
   var dirs: List<MyPath> = listOf()

   @Description("Ignore this maximum difference in seconds when comparing dates")
   var ignoreSecondsDiff: Int = 0

   @Description("Ignore this maximum difference in hours when comparing dates")
   var ignoreHoursDiff: Int = 0
}

class RenameFilesToModificationDateParams {
   @Description("Directories")
   var dirs: List<MyPath> = listOf()
}

class ListIndexRunsParams {
   @Description("Directory")
   var dir: MyPath? = null
}

class ListPathsParams {
   @Description("Directory")
   var dir: MyPath? = null
}

class VerifyFilesParams {
   @Description("Directory")
   var dir: MyPath? = null
}

class RemoveIndexRunParams {
   @Description("Index number")
   var indexNr: Int? = null

   @Description("Range of index numbers")
   var indexNrRange: IntRange? = null
}

class CompareIndexRunsParams {
   enum class CompareIndexRunsResult { LEFT, RIGHT, BOTH }

   @Description("Index 1 number")
   var indexNr1: Int = 0

   @Description("Index 2 number")
   var indexNr2: Int = 0

   var result: CompareIndexRunsResult? = null
}

class ImportOldDbParams {
   @Description("Old DB file")
   var oldDb: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
}

class FilterFilesParams {
   @Description("Filter")
   var path: String? = null
   var file: String? = null
   var isJavaRegex: Boolean = false
   var minSize: Long? = null
   var maxSize: Long? = null
}

class DeleteFilesParams {
   var deleteEmptyDirs: Boolean = true
}

class MoveFilesParams {
   var basePath: MyPath? = null
   var toDir: MyPath? = null
   var deleteEmptyDirs: Boolean = true
}

class PrintFilesParams {
   var folderOnly: Boolean = true
   var details: Boolean = true
}