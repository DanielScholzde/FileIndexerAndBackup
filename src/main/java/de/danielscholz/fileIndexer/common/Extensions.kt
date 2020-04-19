package de.danielscholz.fileIndexer.common

import org.apache.commons.lang3.StringUtils
import java.util.*

fun String?.isEmpty(): Boolean {
   return StringUtils.isEmpty(this)
}

fun String?.isNotEmpty(): Boolean {
   return StringUtils.isNotEmpty(this)
}

fun String?.ifEmpty(emptyReturn: String): String {
   return if (this == null || this.isEmpty()) emptyReturn else this
}

fun String.getSha1Chunk(): String {
   return substring(0, 12)
}

fun <T : Any> T?.ifNull(emptyReturn: T): T {
   return this ?: emptyReturn
}

fun Long.ifZero(alternateValue: Long) = if (this == 0L) alternateValue else this
fun Int.ifZero(alternateValue: Int) = if (this == 0) alternateValue else this

fun <R : Any?> Boolean.ifTrue(ifTrueReturn: R, ifFalseReturn: R): R {
   return if (this) ifTrueReturn else ifFalseReturn
}

fun String.substring2(startIndex: Int, maxEndIndex: Int): String {
   return this.substring(startIndex, if (maxEndIndex <= this.length) maxEndIndex else this.length)
}

fun String.ensureSuffix(suffix: String): String {
   return if (this.endsWith(suffix)) this else this + suffix
}

fun String.ensurePrefix(prefix: String): String {
   return if (this.startsWith(prefix)) this else prefix + this
}

fun String.insert(pos: Int, str: String): String {
   return this.substring(0, pos) + str + this.substring(pos)
}

fun String.countStr(str: String): Int {
   return StringUtils.countMatches(this, str)
}

fun String.leftPad(size: Int): String {
   return StringUtils.leftPad(this, size)
}

fun String.leftPad(size: Int, char: Char): String {
   return StringUtils.leftPad(this, size, char)
}

fun Collection<String>.convertToSortedStr(): String {
   return TreeSet(this).joinToString("|")
}

fun String.getFileExtension(): String? {
   val ext = substringAfterLast('.', "")
   return if (ext.isNotBlank()) ext else null
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the collection.
 */
inline fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
   var sum: Long = 0
   for (element in this) {
      sum += selector(element)
   }
   return sum
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 */
inline fun <T> Array<out T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
   var i = 0
   for (element in this) {
      if (predicate(i, element)) {
         return true
      }
      i++
   }
   return false
}