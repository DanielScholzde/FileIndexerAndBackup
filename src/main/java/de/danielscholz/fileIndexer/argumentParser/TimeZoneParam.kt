package de.danielscholz.fileIndexer.argumentParser

import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.parser.ParamParserBase
import java.util.*

class TimeZoneParam : ParamParserBase<TimeZone, TimeZone?>() {

   override var callback: ((TimeZone) -> Unit)? = null
   private var value: TimeZone? = null

   override fun matches(rawValue: String): Boolean {
      return TimeZone.getAvailableIDs().contains(rawValue)
   }

   override fun assign(rawValue: String) {
      value = TimeZone.getTimeZone(rawValue)
   }

   override fun exec() {
      callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
   }

   override fun convertToStr(value: TimeZone?): String? {
      return if (value != null) value.id else null
   }

   override fun printout(): String {
      return "timeZoneID"
   }
}