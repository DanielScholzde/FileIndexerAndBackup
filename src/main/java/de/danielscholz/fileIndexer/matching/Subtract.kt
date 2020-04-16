package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.common.mutableListMultimapOf
import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*

/**
 * Calculates collection1 minus collection2
 */
class Subtract(private val mode: EnumSet<MatchMode>, private val multimapMatching: Boolean) {

   fun apply(collection1: Collection<FileLocation>, collection2: Collection<FileLocation>): Collection<FileLocation> {

      if (multimapMatching) {
         val collection1AsMultimap = mutableListMultimapOf<String, FileLocation>()

         collection1.forEach {
            collection1AsMultimap.put(createKey(it, mode), it)
         }

         collection2.forEach {
            collection1AsMultimap.removeAll(createKey(it, mode))
         }

         return collection1AsMultimap.values()
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
         collection1AsMap.remove(key)
      }

      return collection1AsMap.values
   }

}
