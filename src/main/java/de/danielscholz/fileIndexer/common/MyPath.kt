package de.danielscholz.fileIndexer.common

import java.io.File

class MyPath private constructor(val path: String, val mediumSerial: String?, val mediumSerialAutodetected: Boolean) {

   companion object {
      fun of(pathWithSerialBeginning: String): MyPath {
         var path = pathWithSerialBeginning
         var mediumSerial: String? = null
         if (path.startsWith("[")) {
            if (path.contains("]")) {
               val indexOf = path.indexOf(']')
               mediumSerial = path.substring(1, indexOf)
               path = path.substring(indexOf + 1)
            }
         }
         return if (mediumSerial != null) MyPath(path, mediumSerial) else MyPath(path)
      }
   }

   constructor(path: String) : this(path, getVolumeSerialNr(path), true) {
   }

   constructor(path: String, mediumSerial: String?) : this(path, mediumSerial, false) {
   }

   fun toFile() = File(path)
   fun toPath() = toFile().toPath()

   val canonicalFile get() = MyPath(toFile().canonicalFile.path)

   val prefix get() = calcFilePathPrefix(path)

   val pathWithoutPrefix get() = calcPathWithoutPrefix(path)

   override fun toString(): String {
      return path
   }
}