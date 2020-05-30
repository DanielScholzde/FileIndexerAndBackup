package de.danielscholz.fileIndexer.common

import org.apache.commons.lang3.StringUtils

fun List<String>.alignColumnsOfAllRows(separator: Regex): List<String> {
   fun String.isNumber(): Boolean {
      if (!this.isEmpty()) {
         var num = false
         for (i in 0..this.lastIndex) {
            if (this[i] in '0'..'9') {
               num = true
            } else {
               if (this[i] == '.' || this[i] == ',') {
                  if (!num) return false
               } else return false
            }
         }
         return true
      }
      return false
   }

   fun String.isClosingParenthesis(): Boolean {
      if (!this.isEmpty()) {
         return this == ")" || this == "]"
      }
      return false
   }

   val listOfLists = this.map { it.split(separator) }
   val maxColumnCount = listOfLists.map { it.size }.max() ?: 0

   for (i in 0 until maxColumnCount) {
      val max = listOfLists.map { if (i <= it.lastIndex) it[i] else "" }.map { it.length }.max() ?: 0
      listOfLists.forEach {
         val s = it[i]
         if (i <= it.lastIndex && s.length < max) {
            if (s.trim().isNumber() || s.trim().isClosingParenthesis()) {
               (it as MutableList<String>)[i] = StringUtils.leftPad(s, max)
            } else {
               (it as MutableList<String>)[i] = StringUtils.rightPad(s, max)
            }
         }
      }
   }
   return listOfLists.map { StringUtils.join(it, "") }
}