package de.danielscholz.fileIndexer.matching

enum class MatchMode {
   HASH,

   //HASH_BEGIN,
   HASH_BEGIN_1MB,

   //HASH_END,
   FILE_SIZE,
   CREATED,
   MODIFIED_MILLIS,
   MODIFIED_SEC,

   /** Inode number by which hard links can be recognized */
   REF_INODE,

   /** complete path with prefix but without filename */
   FULL_PATH,

   /** complete path without prefix and filename */
   FULL_PATH_EXCL_PREFIX,

   /** relative path without filename. Entries in Global.relPathRoots are considered. */
   REL_PATH,

   /** relative path within one indexRun layer without filename. Path starts from indexRun root directory. */
   REL_PATH2,

   /** filename without path */
   FILENAME
}