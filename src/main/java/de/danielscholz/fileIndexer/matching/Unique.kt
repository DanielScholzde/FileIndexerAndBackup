package de.danielscholz.fileIndexer.matching

import de.danielscholz.fileIndexer.persistence.FileLocation
import java.util.*

/**
 * Calculates a unique collection. If errorOnKeyCollision = false and there are more then one entry with the calculated ID only the first entry is returned.
 */
class Unique(private val mode: EnumSet<MatchMode>, private val errorOnKeyCollision: Boolean) {

   fun apply(collection: Collection<FileLocation>): Collection<FileLocation> {

      val map = LinkedHashMap<String, FileLocation>()

      collection.forEach {
         val key = createKey(it, mode)
         if (!map.containsKey(key)) {
            map[key] = it
         } else if (errorOnKeyCollision) {
            throw Exception("Error: The match mode $mode creates duplicates!")
         }
      }

      return map.values
   }

}