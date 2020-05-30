package de.danielscholz.fileIndexer.persistence

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import de.danielscholz.fileIndexer.common.isNotEmpty
import de.danielscholz.fileIndexer.common.listOf
import java.io.File

class FilePathCache(val pl: PersistenceLayer) {

   fun searchOrInsertFilePath(filePath: FilePath): FilePath {
      synchronized(this) {
         val filePathFound = searchFilePath(filePath.pathPart, filePath.parentFilePathId)
         if (filePathFound != null) {
            return filePathFound
         }
         val filePathInserted = pl.insertIntoFilePath(filePath)
         clearFilePathCache(filePathInserted)
         return filePathInserted
      }
   }

   fun getOrInsertFullFilePath(dir: File): FilePath {
      synchronized(this) {
         var parentFilePath = getFilePath(Queries.filePathRootId)
         for ((dirName, path) in splitPath(dir)) {
            parentFilePath = searchOrInsertFilePath(
                  FilePath(0, pl, parentFilePath.id, path, dirName, parentFilePath.depth + 1))
         }
         return parentFilePath
      }
   }

   /**
    * Sucht in der Tabelle FilePath nach dem kompletten Verzeichnis-Pfad und liefert das Ergebnis zurück ODER null
    */
   fun getFilePath(dir: File): FilePath? {
      synchronized(this) {
         return searchFilePath(convertDir(dir))
      }
   }

   fun getFilePath(filePathId: Long): FilePath {
      synchronized(this) {
         return filePathByIdCache.get(filePathId)
      }
   }

   fun clearFilePathCache(filePath: FilePath? = null) {
      synchronized(this) {
         if (filePath != null) {
            filePathByIdCache.invalidate(filePath.id)
            filePathByPathPartAndParentIdCache.invalidate(Pair(filePath.pathPart, filePath.parentFilePathId))
            filePathByPathCache.invalidate(filePath.path)
         } else {
            filePathByIdCache.invalidateAll()
            filePathByPathPartAndParentIdCache.invalidateAll()
            filePathByPathCache.invalidateAll()
         }
      }
   }

   //------------------------------------------------------------------------------------------

   private val filePathByIdCache: LoadingCache<Long, FilePath> = CacheBuilder.newBuilder()
      .maximumSize(10000)
      .build(object : CacheLoader<Long, FilePath>() {
         override fun load(filePathId: Long): FilePath {
            return pl.db.dbQueryUnique(Queries.filePath3, listOf(filePathId)) {
               pl.extractFilePath(it)!!
            }
         }
      })!!

   private fun searchFilePath(pathPart: String, parentFilePathId: Long?): FilePath? {
      val cachedValue = filePathByPathPartAndParentIdCache.get(Pair(pathPart, parentFilePathId ?: -1))
      return if (cachedValue !== nullElem) cachedValue else null
   }

   private fun searchFilePath(path: String): FilePath? {
      val cachedValue = filePathByPathCache.get(path)
      return if (cachedValue !== nullElem) cachedValue else null
   }

   private val nullElem = FilePath(-1, pl, -1, "", "", -1)

   // Thread-safe
   private val filePathByPathPartAndParentIdCache: LoadingCache<Pair<String, Long>, FilePath> = CacheBuilder.newBuilder()
      .maximumSize(10000)
      .build(object : CacheLoader<Pair<String, Long>, FilePath>() {
         override fun load(key: Pair<String, Long>): FilePath {
            val result = pl.db.dbQueryUniqueNullable(Queries.filePath1, listOf(key.first, key.second)) {
               pl.extractFilePath(it)
            }
            return result ?: nullElem
         }
      })!!

   private val filePathByPathCache: LoadingCache<String, FilePath> = CacheBuilder.newBuilder()
      .maximumSize(10000)
      .build(object : CacheLoader<String, FilePath>() {
         override fun load(key: String): FilePath {
            val result = pl.db.dbQueryUniqueNullable(Queries.filePath2, listOf(key)) {
               pl.extractFilePath(it)
            }
            return result ?: nullElem
         }
      })!!

   /**
    * Splittet einen Verzeichnis-Pfad in seine einzelnen Bestandteile
    */
   private fun splitPath(dir: File): List<Pair<String, String>> {
      val pathPartsResult = mutableListOf<Pair<String, String>>()
      var dir2: File? = dir

      while (dir2 != null) {
         if (dir2.name.isNotEmpty()) {
            pathPartsResult.add(0, Pair(dir2.name, convertDir(dir2)))
            dir2 = dir2.parentFile
         } else break
      }
      return pathPartsResult
   }

   private fun String.addSlashes(): String {
      val startsWith = startsWith("/")
      val endsWith = endsWith("/")
      if (startsWith && endsWith) return this
      if (startsWith && !endsWith) return "$this/"
      if (!startsWith && endsWith) return "/$this"
      return "/$this/"
   }

   private fun convertDir(dir: File): String = dir.path.replace('\\', '/').addSlashes()

   fun getObjectCount(): String {
      return "Objects in FilePathCache: " +
             (filePathByIdCache.size() +
              filePathByPathCache.size() +
              filePathByPathPartAndParentIdCache.size())
   }
}