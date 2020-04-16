package de.danielscholz.fileIndexer

import java.io.File

/**
 * Global properties that are required by the whole program.
 */
object Global {

   const val programVersion = 1 // must match the DB version and should be increased with every database change

   var stat = Statistic()

   @Volatile
   var cancel = false

   val relPathRoots = mutableListOf<String>()

   /** Callback for created directories. Must be threadsafe!! */
   var createdDirsCallback: (File) -> Unit = {}

   /** Callback for created files. Must be threadsafe!! */
   var createdFilesCallback: (File) -> Unit = {}

   fun echoAndResetStat() {
      stat.echoStatistics()
      stat = Statistic()
   }
}