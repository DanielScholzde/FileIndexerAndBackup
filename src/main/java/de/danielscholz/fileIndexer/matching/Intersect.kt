package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.common.mutableListMultimapOf
import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*
import kotlin.math.min

/**
 * Calculates the intersection of two collections.
 */
class Intersect(val mode: EnumSet<MatchMode>, private val multimapMatching: Boolean) {

   fun apply(collection1: Collection<FileLocation>,
             collection2: Collection<FileLocation>): List<Pair<FileLocation, FileLocation>> {

      val result = mutableListOf<Pair<FileLocation, FileLocation>>()
      if (result is ArrayList) {
         result.ensureCapacity(min(collection1.size, collection2.size) / 10)
      }

      if (multimapMatching) {
         val collection1AsMultimap = mutableListMultimapOf<String, FileLocation>()

         collection1.forEach {
            collection1AsMultimap.put(createKey(it, mode), it)
         }

         collection2.forEach {
            val fileLocations = collection1AsMultimap[createKey(it, mode)]
            for (fileLocation in fileLocations) {
               result.add(Pair(fileLocation, it))
            }
         }

         return result
      }

      val collection1AsMap = HashMap<String, FileLocation>()

      collection1.forEach {
         if (collection1AsMap.put(createKey(it, mode), it) != null) {
            throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
         }
      }

      val collection2Keys = mutableSetOf<String>()

      collection2.forEach {
         val key = createKey(it, mode)
         if (!collection2Keys.add(key)) {
            throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
         }
         val fileLocation = collection1AsMap[key]
         if (fileLocation != null) {
            result.add(Pair(fileLocation, it))
         }
      }

      return result
   }

}