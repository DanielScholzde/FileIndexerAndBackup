package de.danielscholz.fileIndexer.common

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

@Suppress("BlockingMethodInNonBlockingContext", "DuplicatedCode")
suspend fun processArchiveSuspending(
   archive: File,
   processEntry: suspend (InputStreamWrapper, ArchiveEntry) -> Unit,
   processFailure: (Exception) -> Unit
): Boolean {

   suspend fun processArchiveStream(archiveInputStream: ArchiveInputStream) {
      while (true) {
         val entry = archiveInputStream.nextEntry ?: break
         if (!archiveInputStream.canReadEntryData(entry)) {
            processFailure(Exception("Archive entry could not be read: ${entry.name}"))
            continue
         }
         if (!entry.isDirectory) {
            processEntry(InputStreamWrapperImpl(archiveInputStream), entry)
         }
         testIfCancel()
      }
   }

   try {
      if (archive.name.lowercase().endsWith(".zip")) {
         // Cp437 ist der Default (MS-DOS Encoding)
         ZipFile(archive, "Cp437").tryWith { zipFile ->
            for (zipArchiveEntry in zipFile.entriesInPhysicalOrder) {
               if (!zipArchiveEntry.isDirectory) {
                  zipFile.getInputStream(zipArchiveEntry).tryWith { inputStream ->
                     processEntry(InputStreamWrapperImpl(inputStream), zipArchiveEntry)
                  }
               }
               if (testIfCancelNoException()) {
                  break
               }
            }
         }
      } else if (archive.name.lowercase().endsWith(".tar.gz")) {
         BufferedInputStream(FileInputStream(archive)).tryWith { stream ->
            TarArchiveInputStream(GzipCompressorInputStream(stream)).tryWith { archiveInputStream ->
               processArchiveStream(archiveInputStream)
            }
         }
      } else if (!archive.name.lowercase().endsWith(".7z")) {
         BufferedInputStream(FileInputStream(archive)).tryWith { stream ->
            ArchiveStreamFactory().createArchiveInputStream(stream).tryWith { archiveInputStream ->
               processArchiveStream(archiveInputStream)
            }
         }
      } else {
         SevenZFile(archive).tryWith { sevenZFile ->
            while (true) {
               val entry = sevenZFile.nextEntry ?: break
               if (!entry.isDirectory) {
                  processEntry(
                     object : InputStreamWrapper {
                        override fun read(b: ByteArray): Int = sevenZFile.read(b)
                        override fun close() {}
                     },
                     entry
                  )
               }
               testIfCancel()
            }
         }
      }
      return true
   } catch (e: UnsupportedZipFeatureException) {
      processFailure(e)
   } catch (e: ArchiveException) {
      processFailure(e)
   } catch (e: IOException) {
      processFailure(e)
   }
   return false
}