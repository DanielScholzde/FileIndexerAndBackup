package de.danielscholz.fileIndexer

enum class Commands(val command: String) {
   INDEX_FILES("index"),
   SYNC_FILES("sync"),
   BACKUP_FILES("backup"),
   DELETE_DUPLICATES("delete_duplicates"),
   FIND_NOCOPY("find_nocopy"),
   CORRECT_DIFF_MODIFIED("correct_diff_modified"),
   CORRECT_DIFF_MODIFIED_EXIF("correct_diff_modified_exif"),
   RENAME_TO_MODIFIED_DATE("rename_to_modified_date"),
   SHOW_INFOS("show_infos"),
   LIST_INDEXE("list"),
   LIST_PATHS("list_paths"),
   REMOVE_INDEX("remove"),
   COMPARE_INDEXE("compare"),
   VERIFY("verify"),
   IMPORT_OLD_DB("import_old_db"),
   CONSOLE("console"),
   EXIT("exit")
}