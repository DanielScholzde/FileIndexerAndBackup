package de.danielscholz.fileIndexer.actions

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.ChecksumCreator
import de.danielscholz.fileIndexer.common.InputStreamWrapperImpl
import de.danielscholz.fileIndexer.common.tryWith
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.PersistenceLayer
import de.danielscholz.fileIndexer.persistence.getFullFilePath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant

// todo implement missing
class VerifyFiles(private val pl: PersistenceLayer, private val doPrintln: Boolean) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun run(dir: File, excludeIndexRunsWithFailures: Boolean = false, checkIfAllFilesAreInIndex: Boolean = false): Boolean {
      val source = pl.loadFileLocationsForPath("auto", dir, excludeIndexRunsWithFailures, false)
      var diffs = 0

      if (checkIfAllFilesAreInIndex) {
//         val multimap = source.listMultimapBy { it.filePathId }
//         for (filePathId in multimap.keySet()) {
//            val list = multimap.get(filePathId)
//            for (FileLocation )
//            val fullFilePath = getFullFilePath(sourcePath.indexRun, fileLocation)
//            val file = File(fullFilePath)
//
//            val attributes = Files.readAttributes(file.toPath(),
//                                                  BasicFileAttributes::class.java,
//                                                  LinkOption.NOFOLLOW_LINKS)
//
//            if (!verifyFile(attributes.lastModifiedTime().toLocalDateTime(),
//                            attributes.size(),
//                            fileLocation,
//                            file)) {
//               diffs++
//               logger.warn("Difference detected: $fullFilePath")
//            }
//         }
      } else {
         for (fileLocation in source) {
            val file = File(fileLocation.getFullFilePath())
            if (!file.isFile) {
               diffs++
               myPrintln("$file is missing")
            } else if (!verifyFile(file, fileLocation)) {
               diffs++
            }
         }
      }

      if (diffs == 0) myPrintln("No difference found")

      return diffs == 0
   }

   private fun verifyFile(file: File, fileLocation: FileLocation): Boolean {
      val attributes = Files.readAttributes(
         file.toPath(),
         BasicFileAttributes::class.java,
         LinkOption.NOFOLLOW_LINKS
      )

      return verifyFile(
         attributes.lastModifiedTime().toInstant(),
         attributes.size(),
         fileLocation,
         file
      )
   }

   private fun verifyFile(
      modified: Instant,
      fileSize: Long,
      fileLocation: FileLocation,
      file: File
   ): Boolean {
      if (fileSize == fileLocation.fileContent!!.fileSize) {
         if (modified == fileLocation.modified) {
            if (Config.INST.fastMode && Config.INST.ignoreHashInFastMode) {
               return true
            }
            FileInputStream(file).tryWith {
               val checksumCreator = ChecksumCreator(InputStreamWrapperImpl(it), fileSize)
               if (Config.INST.fastMode) {
                  val checksumFromBeginTemp = checksumCreator.getChecksumFromBeginTemp().joinToString(",")
                  if (fileLocation.fileContent!!.hashBegin.startsWith(checksumFromBeginTemp)) {
                     return true
                  } else {
                     myPrintln("$file has different hash")
                     return false
                  }
               }
               val equal = fileLocation.fileContent!!.hash == checksumCreator.calcChecksum().sha1
               if (!equal) {
                  myPrintln("$file has different hash")
               }
               return equal
            }
         } else myPrintln("$file file modification date is different")
      } else myPrintln("$file file size is different")
      return false
   }

   private fun myPrintln(str: String) {
      if (doPrintln) logger.info(str)
   }
}