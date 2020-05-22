package de.danielscholz.fileIndexer

enum class Commands(val command: String) {
   INDEX_FILES("index"),
   SYNC_FILES("sync"),
   BACKUP_FILES("backup"),
   FIND_DUPLICATE_FILES("findDuplicates"),
   FIND_FILES_WITH_NO_COPY("findFilesWithNoCopy"),
   CORRECT_DIFF_IN_FILE_MODIFICATION_DATE("correctDiffInFileModificationDate"),
   CORRECT_DIFF_IN_FILE_MODIFICATION_DATE_AND_EXIF_DATE_TAKEN("correctDiffInFileModificationDateAndExifDateTaken"),
   RENAME_FILES_TO_MODIFICATION_DATE("renameFilesToModificationDate"),
   SHOW_DATABASE_REPORT("showDatabaseReport"),
   LIST_INDEX_RUNS("list"),
   LIST_PATHS("listPaths"),
   REMOVE_INDEX_RUN("removeIndex"),
   COMPARE_INDEX_RUNS("compare"),
   VERIFY_FILES("verify"),
   IMPORT_OLD_DB("importOldDb"),
   CONSOLE("console"),
   STATUS("status"),
   FILTER("filter"),
   DELETE("delete"),
   PRINT("print"),
   EXIT("exit")
}