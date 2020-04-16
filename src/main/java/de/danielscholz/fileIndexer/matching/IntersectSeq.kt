package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.common.mutableListMultimapOf
import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*

/**
 * Calculates the intersection of two collections.
 */
class IntersectSeq(private val mode: EnumSet<MatchMode>, private val multimapMatching: Boolean) {

   fun apply(collection1: Sequence<FileLocation>,
             collection2: Sequence<FileLocation>): Sequence<Pair<FileLocation, FileLocation>> {

      if (multimapMatching) {
         val collection1AsMultimap = mutableListMultimapOf<String, FileLocation>()

         collection1.forEach {
            collection1AsMultimap.put(createKey(it, mode), it)
         }

         return sequence {
            collection2.forEach {
               val fileLocations = collection1AsMultimap[createKey(it, mode)]
               for (fileLocation in fileLocations) {
                  yield(Pair(fileLocation, it))
               }
            }
         }
      }

      val collection1AsMap = HashMap<String, FileLocation>()

      collection1.forEach {
         if (collection1AsMap.put(createKey(it, mode), it) != null) {
            throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
         }
      }

      val collection2Keys = HashSet<String>()

      return sequence {
         collection2.forEach {
            val key = createKey(it, mode)
            if (!collection2Keys.add(key)) {
               throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
            }
            val fileLocation = collection1AsMap[key]
            if (fileLocation != null) {
               yield(Pair(fileLocation, it))
            }
         }
      }

   }

}