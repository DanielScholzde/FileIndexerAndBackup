package de.danielscholz.fileIndexer

import java.io.File

class GlobalParams {
   var db: File? = null
}

class IndexFilesParams {
   var dirs: List<File> = listOf()
   var lastIndexDir: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
   var timeZone: String? = null
   var noArchiveContents: Boolean = false
   var updateHardlinksInLastIndex: Boolean = false
}

class SyncFilesParams {
   var sourceDir: File? = null
   var targetDir: File? = null
   var mediumDescriptionSource: String? = null
   var mediumDescriptionTarget: String? = null
   var mediumSerialSource: String? = null
   var mediumSerialTarget: String? = null
   var timeZone: String? = null
   var skipIndexFilesOfSourceDir: Boolean = false
}

class BackupFilesParams {
   var sourceDir: File? = null
   var targetDir: File? = null
   var mediumDescriptionSource: String? = null
   var mediumDescriptionTarget: String? = null
   var mediumSerialSource: String? = null
   var mediumSerialTarget: String? = null
   var timeZone: String? = null
   var skipIndexFilesOfSourceDir = false
   var indexArchiveContentsOfSourceDir = false
}

class FindDuplicatesParams {
   var dirs: List<File> = listOf()
   var inclFilenameOnCompare: Boolean = false
   var printOnlyDeleted: Boolean = false
   var deleteDuplicates: Boolean = false
   var deletePathFilter: String? = null
}

class FindNoCopyParams {
   var referenceDir: File? = null
   var toSearchInDirs: List<File> = listOf()
   var reverse: Boolean = false
}

class FindDiffInModifiedDateParams {
   var referenceDir: File? = null
   var toSearchInDirs: List<File> = listOf()
   var ignoreMilliseconds: Boolean = false
}

class FindDiffInModifiedExifParams {
   var dirs: List<File> = listOf()
   var ignoreSecondsDiff: Int = 0
   var ignoreHoursDiff: Int = 0
}

class RenameToModifiedDateParams {
   var dirs: List<File> = listOf()
}

class ListIndexeParams {
   var dir: File? = null
}

class ListPathsParams {
   var dir: File? = null
}

class VerifyFilesParams {
   var dir: File? = null
}

class RemoveIndexParams {
   var indexNr: Int? = null
   var indexNrRange: IntRange? = null
}

class CompareIndexeParams {
   var indexNr1: Int = 0
   var indexNr2: Int = 0
}

class ImportOldDbParams {
   var oldDb: File? = null
   var mediumDescription: String? = null
   var mediumSerial: String? = null
}