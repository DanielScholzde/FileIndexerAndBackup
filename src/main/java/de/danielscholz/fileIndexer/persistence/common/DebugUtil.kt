package de.danielscholz.fileIndexer.persistence.common

import de.danielscholz.fileIndexer.common.*

fun Database.queryDebug(sql: String): String {
   val result = mutableListOf<MutableList<String>>()

   this.dbQuery(sql, listOf()) {
      var list = mutableListOf<String>()
      if (result.isEmpty()) {
         for (i in 1..it.metaData.columnCount) {
            list.add(it.metaData.getColumnName(i))
         }
         result.add(list)
         list = mutableListOf()
      }
      for (i in 1..it.metaData.columnCount) {
         val value = it.getObject(i)
         if (value is String) {
            list.add("\"" + value + "\"")
         } else {
            var s = value?.toString() ?: ""
            var date = 0L
            if (value is Int) {
               date = value.toLong()
            } else if (value is Long) {
               date = value
            }
            if (date in 100000000000L..2500000000000L) {
               s = date.toInstant().convertToUtcZone().toStrSys() + "(UTC)"
            }
            list.add(s)
         }
      }
      result.add(list)
   }

   return result.map { it.joinToString(" @@|@@ ") }
             .alignColumnsOfAllRows(Regex("@@"))
             .joinToString("\n") + "\n"
}