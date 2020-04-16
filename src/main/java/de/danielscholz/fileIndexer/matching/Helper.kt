package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.ignoreMillis
import de.danielscholz.fileIndexer.common.substring2
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.getFullPath
import de.danielscholz.fileIndexer.persistence.getFullPathExclPrefix
import java.util.*

fun createKey(fileLocation: FileLocation, mode: EnumSet<MatchMode>): String {
   val b = StringBuilder(100)
   if (MatchMode.FILE_SIZE in mode) {
      b.append(fileLocation.fileContent?.fileSize ?: 0).append("@")
   }
   if (MatchMode.HASH in mode) {
      b.append(fileLocation.fileContent?.hash).append("@")
   }
   //if (MatchMode.HASH_BEGIN in mode) {
   //   b.append(fileLocation.fileContent?.hashBegin).append("@")
   //}
   if (MatchMode.HASH_BEGIN_1MB in mode) {
      b.append(fileLocation.fileContent?.hashBegin?.substring2(0, 11 * 12 + 10 * 1)).append("@")
   }
   //if (MatchMode.HASH_END in mode) {
   //   b.append(fileLocation.fileContent?.hashEnd).append("@")
   //}
   // Workaround für leere Dateien
   if (fileLocation.fileContent == null && MatchMode.FILENAME !in mode && (MatchMode.FILE_SIZE in mode || MatchMode.HASH in mode || MatchMode.HASH_BEGIN_1MB in mode)) {
      b.append(fileLocation.filename).append("@")
   }
   if (MatchMode.FULL_PATH in mode) {
      val path = fileLocation.getFullPath()
      b.append(if (fileLocation.indexRun!!.mediumCaseSensitive) path else path.toLowerCase()).append("@")
   }
   if (MatchMode.FULL_PATH_EXCL_PREFIX in mode) {
      val path = fileLocation.getFullPathExclPrefix()
      b.append(if (fileLocation.indexRun!!.mediumCaseSensitive) path else path.toLowerCase()).append("@")
   }
   if (MatchMode.REL_PATH in mode) {
      val mediumCaseSensitive = fileLocation.indexRun!!.mediumCaseSensitive
      var path = fileLocation.pl.getFilePath(fileLocation.indexRun!!.filePathId).path +
                 fileLocation.pl.getFilePath(fileLocation.filePathId).path
      for (relPath in Global.relPathRoots) {
         if (path.startsWith(relPath, !mediumCaseSensitive)) {
            path = path.substring(relPath.length)
            break
         }
      }
      b.append(if (mediumCaseSensitive) path else path.toLowerCase()).append("@")
   }
   if (MatchMode.REL_PATH2 in mode) {
      val mediumCaseSensitive = fileLocation.indexRun!!.mediumCaseSensitive
      val path = fileLocation.pl.getFilePath(fileLocation.filePathId).path
      b.append(if (mediumCaseSensitive) path else path.toLowerCase()).append("@")
   }
   if (MatchMode.FILENAME in mode) {
      b.append(fileLocation.filename).append("@")
   }
   if (MatchMode.MODIFIED_MILLIS in mode) {
      b.append(fileLocation.modified).append("@")
   }
   if (MatchMode.MODIFIED_SEC in mode) {
      b.append(fileLocation.modified.ignoreMillis()).append("@")
   }
   if (MatchMode.CREATED in mode) {
      b.append(fileLocation.created).append("@")
   }
   if (MatchMode.REF_INODE in mode) {
      b.append(fileLocation.referenceInode).append("@")
   }
   return b.toString()
}

operator fun MatchMode.plus(b: MatchMode): EnumSet<MatchMode> {
   return EnumSet.of(this, b)
}

operator fun EnumSet<MatchMode>.plus(b: MatchMode): EnumSet<MatchMode> {
   this.add(b)
   return this
}


fun Collection<FileLocation>.subtract(other: Collection<FileLocation>,
                                      mode: EnumSet<MatchMode>,
                                      multimapMatching: Boolean): Collection<FileLocation> {
   return Subtract(mode, multimapMatching).apply(this, other)
}

fun Sequence<FileLocation>.subtract(other: Sequence<FileLocation>,
                                    mode: EnumSet<MatchMode>,
                                    multimapMatching: Boolean): Sequence<FileLocation> {
   return SubtractSeq(mode, multimapMatching).apply(this, other)
}

fun Collection<FileLocation>.intersect(other: Collection<FileLocation>,
                                       mode: EnumSet<MatchMode>,
                                       multimapMatching: Boolean): Collection<Pair<FileLocation, FileLocation>> {
   return Intersect(mode, multimapMatching).apply(this, other)
}

fun Sequence<FileLocation>.intersect(other: Sequence<FileLocation>,
                                     mode: EnumSet<MatchMode>,
                                     multimapMatching: Boolean): Sequence<Pair<FileLocation, FileLocation>> {
   return IntersectSeq(mode, multimapMatching).apply(this, other)
}

fun Collection<FileLocation>.union(other: Collection<FileLocation>,
                                   mode: EnumSet<MatchMode>,
                                   errorOnKeyCollision: Boolean): Collection<FileLocation> {
   return Union(mode, errorOnKeyCollision).apply(this, other)
}

fun Sequence<FileLocation>.union(other: Sequence<FileLocation>,
                                 mode: EnumSet<MatchMode>,
                                 errorOnKeyCollision: Boolean): Sequence<FileLocation> {
   return UnionSeq(mode, errorOnKeyCollision).apply(this, other)
}


fun Collection<FileLocation>.makeUnique(mode: EnumSet<MatchMode>, errorOnKeyCollision: Boolean = false): Collection<FileLocation> {
   return Unique(mode, errorOnKeyCollision).apply(this)
}

fun Collection<FileLocation>.filterEmptyFiles(): Collection<FileLocation> {
   return this.filter { it.fileContent != null }
}

fun Sequence<FileLocation>.filterEmptyFiles(): Sequence<FileLocation> {
   return this.filter { it.fileContent != null }
}

fun Collection<Pair<FileLocation, FileLocation>>.filter(resultFilter: ResultFilter): Collection<Pair<FileLocation, FileLocation>> {
   return this.filter { resultFilter.filter(it.first, it.second) }
}

fun Sequence<Pair<FileLocation, FileLocation>>.filter(resultFilter: ResultFilter): Sequence<Pair<FileLocation, FileLocation>> {
   return this.filter { resultFilter.filter(it.first, it.second) }
}

fun Collection<Pair<FileLocation, FileLocation>>.left(): Collection<FileLocation> {
   return this.map { it.first }
}

fun Collection<Pair<FileLocation, FileLocation>>.right(): Collection<FileLocation> {
   return this.map { it.second }
}


infix fun ResultFilter.and(other: ResultFilter): ResultFilter {
   return object : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return this@and.filter(fileLocation1, fileLocation2) && other.filter(fileLocation1, fileLocation2)
      }
   }
}

infix fun ResultFilter.or(other: ResultFilter): ResultFilter {
   return object : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return this@or.filter(fileLocation1, fileLocation2) || other.filter(fileLocation1, fileLocation2)
      }
   }
}

fun not(filter: ResultFilter): ResultFilter {
   return object : ResultFilter {
      override fun filter(fileLocation1: FileLocation, fileLocation2: FileLocation): Boolean {
         return !filter.filter(fileLocation1, fileLocation2)
      }
   }
}