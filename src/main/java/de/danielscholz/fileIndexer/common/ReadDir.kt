package de.danielscholz.fileIndexer.common

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

data class FolderResult(
      val folders: List<Pair<File, List<Path>>>,
      val files: List<File>)

private val logger = LoggerFactory.getLogger("ReadDir")


fun readDir(dir: File, caseSensitive: Boolean, includePaths: List<Path> = listOf()): FolderResult {
   val files: MutableList<File> = mutableListOf()
   val folders: MutableList<Pair<File, List<Path>>> = mutableListOf()
   val filesAndDirs = dir.listFiles()
   if (filesAndDirs != null) {
      for (fileEntry in filesAndDirs.sortedBy { it.name.lowercase() }) {
         if (fileEntry.isDirectory) {
            val matched = matchesPath(fileEntry.name, includePaths, caseSensitive)
            if (includePaths.isEmpty() || matched.isNotEmpty()) {
               if (!isExcludedDir(fileEntry, caseSensitive, null)) {
                  folders.add(fileEntry to matched.removeFirstPathElement())
               }
            }
         } else {
            if (!isExcludedFile(fileEntry, caseSensitive, null)) {
               files.add(fileEntry)
            }
         }
      }
   } else {
      val msg = "ERROR: $dir: Directory is not readable"
      Global.stat.failedDirReads.add(msg)
      logger.error(msg)
   }
   return FolderResult(folders, files)
}


class Excluded(val excludedPathsUsed: MutableSet<String>, val excludedFilesUsed: MutableSet<String>)

class DirInfosResult(var files: Int, var size: Long, var caseSensitive: Boolean, val excluded: Excluded)

class Path(val path: String, val originalPath: String, var used: Boolean = false)

private fun List<Path>.removeFirstPathElement(): List<Path> {
   if (this.isEmpty()) return this
   return this.mapNotNull {
      val s = it.path.removePrefix("/").removeSuffix("/")
      if (s.contains('/')) Path(s.substring(s.indexOf('/') + 1), it.originalPath) else null
   }
}

private fun matchesPath(name: String, includePaths: List<Path>, caseSensitive: Boolean): List<Path> {
   var matched = listOf<Path>()
   if (includePaths.isNotEmpty()) {
      matched = includePaths.filter {
         val b = it.path.equals(name, !caseSensitive) || it.path.startsWith("$name/", !caseSensitive)
         if (b) it.used = true
         b
      }
   }
   return matched
}

fun readAllDirInfos(dir: File, scanArchiveContents: Boolean, includePaths: List<String> = listOf()): DirInfosResult {

   val result = DirInfosResult(0,
                               0,
                               isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem is case sensitive!"),
                               Excluded(mutableSetOf(), mutableSetOf()))

   logger.info("Read all files of directory (incl. subdirectories): $dir [" +
               "included paths: " + (if (includePaths.isEmpty()) "*" else includePaths.joinToString { "\"$it\"" }) + ", " +
               "excluded " +
               "paths: ${Config.INST.excludedPaths.filter { !Config.INST.defaultExcludedPaths.contains(it) }.joinToString { "\"$it\"" }} " +
               "files: ${Config.INST.excludedFiles.filter { !Config.INST.defaultExcludedFiles.contains(it) }.joinToString { "\"$it\"" }}" +
               "]")

   fun readDirIntern(dir: File, includePaths: List<Path>) {
      val filesAndDirs = dir.listFiles()
      if (filesAndDirs != null) {
         for (fileEntry in filesAndDirs.sortedBy { it.name.lowercase() }) {
            val name = fileEntry.name
            val attributes = Files.readAttributes(fileEntry.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isDirectory) {
               val matched = matchesPath(name, includePaths, result.caseSensitive)
               if (includePaths.isEmpty() || matched.isNotEmpty()) {
                  if (!isExcludedDir(fileEntry, result.caseSensitive, result.excluded)) {
                     readDirIntern(fileEntry, matched.removeFirstPathElement())
                  }
               }
            } else if (attributes.isRegularFile) {
               if (!isExcludedFile(fileEntry, result.caseSensitive, result.excluded)) {
                  var archiveRead = false
                  if (scanArchiveContents && fileEntry.extension.lowercase() in Config.INST.archiveExtensions) {
                     archiveRead = processArchive(
                           fileEntry,
                           { _, archiveEntry ->
                              result.files += 1
                              result.size += archiveEntry.size
                           },
                           { exception ->
                              logger.warn("ERROR: $fileEntry: Content of archive could not be read. {}: {}", exception.javaClass.simpleName, exception.message)
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
      if (list.isNotEmpty()) {
         throw Exception("Some includedPaths don't match an existing path: ${list.joinToString()}")
      }
   }

   readDirIntern(dir, includePaths.map { Path(it, it) })

   logger.info("${result.files} files read (${result.size.formatAsFileSize()})")
   return result
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


fun isCaseSensitiveFileSystem(dir: File): Boolean? {

   if (dir.path.lowercase() != dir.path.uppercase() && dir.isDirectory) {
      if (File(dir.path.lowercase()).isDirectory && File(dir.path.uppercase()).isDirectory) {
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
            if (name.lowercase() != name.uppercase()) {
               if (File(fileEntry.path.lowercase()).isDirectory &&
                   File(fileEntry.path.uppercase()).isDirectory) {
                  return false
               }
               return true
            }
            subDirs.add(fileEntry)
         } else {
            if (name.lowercase() != name.uppercase()) {
               if (File(fileEntry.path.lowercase()).isFile &&
                   File(fileEntry.path.uppercase()).isFile) {
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