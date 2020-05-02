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
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

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

suspend fun readAllDirInfos(dir: File, scanArchiveContents: Boolean, includePaths: List<String> = listOf()): DirInfosResult {

   val result = DirInfosResult(0,
                               0,
                               isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem is case sensitive!"),
                               Excluded(mutableSetOf(), mutableSetOf()))

   val logger = LoggerFactory.getLogger("readAllDirInfos")

   logger.info("Read all files of directory (incl. subdirectories): $dir [" +
               "included paths: " + (if (includePaths.isEmpty()) "*" else includePaths.joinToString { "\"$it\"" }) + ", " +
               "excluded " +
               "paths: ${Config.INST.excludedPaths.filter { !Config.INST.defaultExcludedPaths.contains(it) }.joinToString { "\"$it\"" }} " +
               "files: ${Config.INST.excludedFiles.filter { !Config.INST.defaultExcludedFiles.contains(it) }.joinToString { "\"$it\"" }}" +
               "]")

   class Path(val path: String, val originalPath: String, var used: Boolean = false)

   fun List<Path>.removeFirstPath(): List<Path> {
      return this.mapNotNull {
         val s = it.path.removePrefix("/").removeSuffix("/")
         if (s.contains('/')) Path(s.substring(s.indexOf('/') + 1), it.originalPath) else null
      }
   }

   suspend fun readDirIntern(dir: File, includePaths: List<Path>) {
      val filesAndDirs = dir.listFiles()
      if (filesAndDirs != null) {
         for (fileEntry in filesAndDirs) {
            val name = fileEntry.name
            val attributes = Files.readAttributes(fileEntry.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isDirectory) {
               if (includePaths.isEmpty() || includePaths.any {
                        val b = it.path.equals(name, !result.caseSensitive) || it.path.startsWith(name + "/", !result.caseSensitive)
                        if (b) it.used = true
                        b
                     }) {
                  if (!isExcludedDir(fileEntry, result.caseSensitive, result.excluded)) {
                     readDirIntern(fileEntry, includePaths.removeFirstPath())
                  }
               }
            } else if (attributes.isRegularFile) {
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
                              logger.warn("$fileEntry: Content of file could not be read. {}: {}", exception.javaClass.name, exception.message)
                           }
                     )
                  }

                  result.files += 1

                  if (!archiveRead) {
                     result.size += fileEntry.length()
                  }
               }
            } else {
               logger.info("$fileEntry not processed because it is not a directory nor a regular file")
            }
         }
      }
      val list = includePaths.filter { !it.used }.map { it.originalPath }
      if (list.isNotEmpty()) throw Exception("Some includedPaths entries don't match an existing path: ${list.joinToString()}")
   }

   readDirIntern(dir, includePaths.map { Path(it, it) })

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

fun isCaseSensitiveFileSystem(dir: File): Boolean? {

   if (dir.path.toLowerCase() != dir.path.toUpperCase() && dir.isDirectory) {
      if (File(dir.path.toLowerCase()).isDirectory && File(dir.path.toUpperCase()).isDirectory) {
         return false
      }
      return true
   }

   val filesAndDirs = dir.listFiles()
   if (filesAndDirs != null) {
      for (fileEntry in filesAndDirs) {
         val name = fileEntry.name
         val subDirs = mutableListOf<File>()
         if (fileEntry.isDirectory) {
            if (name.toLowerCase() != name.toUpperCase()) {
               if (File(fileEntry.path.toLowerCase()).isDirectory &&
                   File(fileEntry.path.toUpperCase()).isDirectory) {
                  return false
               }
               return true
            }
            subDirs.add(fileEntry)
         } else {
            if (name.toLowerCase() != name.toUpperCase()) {
               if (File(fileEntry.path.toLowerCase()).isFile &&
                   File(fileEntry.path.toUpperCase()).isFile) {
                  return false
               }
               return true
            }
         }
         for (subDir in subDirs) {
            val caseSensitiveFileSystem = isCaseSensitiveFileSystem(subDir)
            if (caseSensitiveFileSystem != null) {
               return caseSensitiveFileSystem
            }
         }
      }
   }
   return null
}