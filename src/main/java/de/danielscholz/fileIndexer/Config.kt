package de.danielscholz.fileIndexer

import de.danielscholz.kargparser.Description

class Config {

   companion object {
      /** single instance, can be replaced at runtime */
      @Volatile
      var INST = Config()
   }

   @Description("Part of filename (without path). To exclude files by extension, use: \".jpg/\"\n" +
                "Hint: a full filename is matched by \"/name/\" (enclosed by separator char)\n" +
                "If the underlying filesystem is case sensitive, these entries are also case sensitive.")
   var excludedFiles: Set<String> = setOf()

   @Description("Part of path (without filename) OR absolute path. Separator char is \"/\".\n" +
                "An absolute path is defined by starting with \"//\", e.g. \"//my/absolute/path/\"\n" +
                "Hint: a full directory name is matched by \"/name/\" (enclosed by separator char)\n" +
                "If the underlying filesystem is case sensitive, these entries are also case sensitive.")
   var excludedPaths: Set<String> = setOf()

   var alwaysCheckHashOnIndexForFilesSuffix: Set<String> = setOf()

   val defaultExcludedFiles = setOf("/thumbs.db/", "/desktop.ini/")
   val defaultExcludedPaths = setOf("/\$RECYCLE.BIN/", "/System Volume Information/")

   //val defaultAlwaysCheckHashOnIndexForFilesSuffix = setOf(".dat", ".db", ".img", ".vhd")
   val imageExtensions = setOf("bmp", "png", "gif", "jpg", "jpeg", "arw", "cr2", "pef")
   val rawImagesExtensions = setOf("arw", "cr2", "pef")

   val archiveExtensions = setOf("zip", "tar", "7z", "gz", "rar")

   var allowMultithreading = true
   var maxThreads = 10
   var createThumbnails = false
   var thumbnailSize = 600

   @Description("When creating a new index, files will only be recognized by size, modification date and hash of first MB.")
   var fastMode = true

   @Description("Should the hash of the first MB also be ignored in fastMode?")
   var ignoreHashInFastMode = false

   @Description("Should the hash only be created with the first MB of the file?\n(faster, but not recommended. May cause problems. Incompatible with indexed files with a hash for full content)")
   var createHashOnlyForFirstMb = false

   @Description("Should a test run be done without any file changes (except the database)?")
   var dryRun = false

   @Description("Should a window with progress information be shown?")
   var progressWindow = true

   // For backup, sync or delete duplicates
   @Description("Should a confirmation popup window for file changes be shown?")
   var confirmations = true

   @Description("Should more information be printed to console?")
   var verbose = false

   // For backup or sync
   @Description("Maximum of allowed file changes in percent. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--silent').")
   var maxChangedFilesWarningPercent = 5

   // For backup or sync
   @Description("Minimum of allowed file changes. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--silent').")
   var minAllowedChanges = 50

   // For backup or sync
   @Description("Minimum of free disk space in percent. If there is not enough disk space, the whole task is not started.")
   var minDiskFreeSpacePercent = 10

   // For backup or sync
   @Description("Minimum of free disk space in MB. If there is not enough disk space, the whole task is not started.")
   var minDiskFreeSpaceMB = 1000

   //---------------------------------- INTERN -------------------------------------------------------
   var maxTransactionSize: Int = 1000
   var maxTransactionDurationSec: Int = 60

}