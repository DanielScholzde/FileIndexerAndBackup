package de.danielscholz.fileIndexer.common

import de.danielscholz.fileIndexer.actions.IndexFiles
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.parser.ParamParserBase

class ReadConfigParam : ParamParserBase<IndexFiles.ReadConfig, IndexFiles.ReadConfig?>() {

   override var callback: ((IndexFiles.ReadConfig) -> Unit)? = null
   private var value: IndexFiles.ReadConfig? = null

   override fun matches(rawValue: String): Boolean {
      return rawValue.toLowerCase() in IndexFiles.ReadConfig.configs.keys.map { it.toLowerCase() }
   }

   override fun assign(rawValue: String) {
      value = IndexFiles.ReadConfig.configs.entries.asSequence().filter { it.key.equals(rawValue, true) }.map { it.value }.firstOrNull()
   }

   override fun exec() {
      callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
   }

   override fun convertToStr(value: IndexFiles.ReadConfig?): String? {
      return IndexFiles.ReadConfig.configs.entries.asSequence().filter { it.value == value }.map { it.key }.firstOrNull()
   }

   override fun printout(): String {
      return "read config: ${IndexFiles.ReadConfig.configs.keys.joinToString("|")}"
   }
}