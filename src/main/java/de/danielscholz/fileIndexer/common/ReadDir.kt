package de.danielscholz.fileIndexer.common

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class FolderResult(
      val folders: List<File>,
      val files: List<File>)

fun readDir(dir: File, caseSensitive: Boolean): FolderResult {
   val files: MutableList<File> = mutableListOf()
   val folders: MutableList<File> = mutableListOf()
   val filesAndDirs = dir.listFiles()
   if (filesAndDirs != null) {
      for (fileEntry in filesAndDirs) {
         if (fileEntry.isDirectory) {
            if (!isExcludedDir(fileEntry, caseSensitive, null)) {
               folders.add(fileEntry)
            }
         } else {
            if (!isExcludedFile(fileEntry, caseSensitive, null)) {
               files.add(fileEntry)
            }
         }
      }
   } else {
      Global.stat.failedDirReads.add(dir.toString())
   }
   return FolderResult(folders, files)
}

private fun isExcludedFile(fileEntry: File, caseSensitive: Boolean, stats: Excluded?): Boolean {
   val name = '/' + fileEntry.name + "/"

   for (excludedFile in Config.INST.excludedFiles.iterator() + Config.INST.defaultExcludedFiles.iterator()) {
      if (name.contains(excludedFile, !caseSensitive)) {
         stats?.excludedFilesUsed?.add(excludedFile)
         return true
      }
   }
   return false
}

private fun isExcludedDir(fileEntry: File, caseSensitive: Boolean, stats: Excluded?): Boolean {
   val path = fileEntry.canonicalPath.replace('\\', '/').ensureSuffix("/")
   val pathWithoutPrefix = myLazy { calcPathWithoutPrefix(fileEntry.canonicalFile) }

   for (excludedPath in Config.INST.excludedPaths.iterator() + Config.INST.defaultExcludedPaths.iterator()) {
      if (excludedPath.startsWith("//")) {
         if (pathWithoutPrefix.value.startsWith(excludedPath.substring(1), !caseSensitive)) {
            stats?.excludedPathsUsed?.add(excludedPath)
            return true
         }
      } else if (path.contains(excludedPath, !caseSensitive)) {
         stats?.excludedPathsUsed?.add(excludedPath)
         return true
      }
   }
   return false
}

data class Excluded(val excludedPathsUsed: MutableSet<String>, val excludedFilesUsed: MutableSet<String>)

data class DirInfosResult(var files: Int, var size: Long, var caseSensitive: Boolean, val excluded: Excluded)

suspend fun readAllDirInfos(dir: File, scanArchiveContents: Boolean): DirInfosResult {
   val result = DirInfosResult(0, 0, true, Excluded(mutableSetOf(), mutableSetOf()))

   val logger = LoggerFactory.getLogger("readAllDirInfos")

   var caseSensitiveTested = false

   suspend fun readDirIntern(dir: File) {
      val filesAndDirs = dir.listFiles()
      if (filesAndDirs != null) {
         for (fileEntry in filesAndDirs) {
            if (fileEntry.isDirectory) {
               if (!caseSensitiveTested && fileEntry.name.toLowerCase() != fileEntry.name.toUpperCase()) {
                  if (File(fileEntry.path.toLowerCase()).isDirectory &&
                      File(fileEntry.path.toUpperCase()).isDirectory) {
                     result.caseSensitive = false
                  }
                  caseSensitiveTested = true
               }


               if (!isExcludedDir(fileEntry, result.caseSensitive, result.excluded)) {
                  readDirIntern(fileEntry)
               }
            } else {
               if (!caseSensitiveTested && fileEntry.name.toLowerCase() != fileEntry.name.toUpperCase()) {
                  if (File(fileEntry.path.toLowerCase()).isFile &&
                      File(fileEntry.path.toUpperCase()).isFile) {
                     result.caseSensitive = false
                  }
                  caseSensitiveTested = true
               }

               if (!isExcludedFile(fileEntry, result.caseSensitive, result.excluded)) {
                  var archiveRead = false
                  if (scanArchiveContents && fileEntry.extension.toLowerCase() in Config.INST.archiveExtensions) {
                     archiveRead = processArchive(
                           fileEntry,
                           { _, archiveEntry ->
                              result.files += 1
                              result.size += archiveEntry.size
                           },
                           { exception ->
                              logger.warn("Content of file could not be read: $fileEntry", exception)
                           }
                     )
                  }

                  result.files += 1

                  if (!archiveRead) {
                     result.size += fileEntry.length()
                  }
               }
            }
         }
      }
   }

   logger.info("Read all files of directory (incl. subdirectories): $dir " + "[excluded " +
               "paths: ${Config.INST.excludedPaths.filter { !Config.INST.defaultExcludedPaths.contains(it) }.joinToString { "\"$it\"" }} " +
               "files: ${Config.INST.excludedFiles.filter { !Config.INST.defaultExcludedFiles.contains(it) }.joinToString { "\"$it\"" }}" +
               "]")

   readDirIntern(dir)

   logger.info("${result.files} files read (${result.size.formatAsFileSize()})")
   return result
}

suspend fun processArchive(archive: File,
                           processEntry: suspend (InputStreamWrapper, ArchiveEntry) -> Unit,
                           processFailure: (Exception) -> Unit): Boolean {

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
      if (archive.name.toLowerCase().endsWith(".zip")) {
         // Cp437 ist der Default (MS-DOS Encoding)
         ZipFile(archive, "Cp437").tryWith { zipFile ->
            for (zipArchiveEntry in zipFile.entriesInPhysicalOrder) {
               if (!zipArchiveEntry.isDirectory) {
                  zipFile.getInputStream(zipArchiveEntry).tryWith { inputStream ->
                     processEntry(InputStreamWrapperImpl(inputStream), zipArchiveEntry)
                  }
               }
            }
         }
      } else if (archive.name.toLowerCase().endsWith(".tar.gz")) {
         BufferedInputStream(FileInputStream(archive)).tryWith { stream ->
            TarArchiveInputStream(GzipCompressorInputStream(stream)).tryWith { archiveInputStream ->
               processArchiveStream(archiveInputStream)
            }
         }
      } else if (!archive.name.toLowerCase().endsWith(".7z")) {
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
                        entry)
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

