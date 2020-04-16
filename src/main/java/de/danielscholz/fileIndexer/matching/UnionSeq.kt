package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*

/**
 * Calculates the union of two collections. Equal entries are taken from collection1!
 */
class UnionSeq(private val mode: EnumSet<MatchMode>, private val errorOnKeyCollision: Boolean) {

   fun apply(collection1: Sequence<FileLocation>, collection2: Sequence<FileLocation>): Sequence<FileLocation> {

      val keys = HashSet<String>()

      return sequence {
         collection1.forEach {
            if (keys.add(createKey(it, mode))) {
               yield(it)
            } else if (errorOnKeyCollision) {
               throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
            }
         }

         val collection2Keys = HashSet<String>()

         collection2.forEach {
            val key = createKey(it, mode)

            if (errorOnKeyCollision && !collection2Keys.add(key)) {
               throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
            }

            if (keys.add(key)) {
               yield(it)
            }
         }
      }
   }

}