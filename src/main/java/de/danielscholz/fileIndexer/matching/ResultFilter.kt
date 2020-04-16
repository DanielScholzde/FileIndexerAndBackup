package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.common.ignoreMillis
import de.danielscholz.fileIndexer.persistence.FileLocation

@Suppress("ClassName")
interface ResultFilter {

   fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean

   object HASH_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (fileLocation1.fileContent?.hash != fileLocation2.fileContent?.hash)
      }
   }

   object FILE_SIZE_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (fileLocation1.fileContent?.fileSize != fileLocation2.fileContent?.fileSize)
      }
   }

   object MODIFIED_MILLIS_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (fileLocation1.modified != fileLocation2.modified)
      }
   }

   object MODIFIED_SEC_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (fileLocation1.modified.ignoreMillis() != fileLocation2.modified.ignoreMillis())
      }
   }

   object ID_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (fileLocation1.id != fileLocation2.id)
      }
   }

   object HARDLINK_NEQ : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return (!isHardlink(fileLocation1, fileLocation2))
      }

      private fun isHardlink(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         // Hardlinks können oft nur innerhalb einer IndexRun-Schicht bestimmt werden!
         //if (it.indexRunId != fileLocation.indexRunId) return false
         return fileLocation1.referenceInode != null && fileLocation2.referenceInode == fileLocation1.referenceInode
      }
   }

}

