package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*

/**
 * Calculates collection1 minus collection2
 */
class SubtractSeq(private val mode: EnumSet<MatchMode>, private val multimapMatching: Boolean) {

   fun apply(collection1: Sequence<FileLocation>, collection2: Sequence<FileLocation>): Sequence<FileLocation> {

      if (multimapMatching) {
         val collection2Keys = HashSet<String>()

         collection2.forEach {
            collection2Keys += createKey(it, mode)
         }

         return sequence {
            collection1.forEach {
               val key = createKey(it, mode)
               if (!collection2Keys.contains(key)) {
                  yield(it)
               }
            }
         }
      }

      val collection2Keys = HashSet<String>()

      collection2.forEach {
         if (!collection2Keys.add(createKey(it, mode))) {
            throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
         }
      }

      val collection1Keys = HashSet<String>()

      return sequence {
         collection1.forEach {
            val key = createKey(it, mode)
            if (!collection1Keys.add(key)) {
               throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
            }
            if (!collection2Keys.contains(key)) {
               yield(it)
            }
         }
      }
   }

}
