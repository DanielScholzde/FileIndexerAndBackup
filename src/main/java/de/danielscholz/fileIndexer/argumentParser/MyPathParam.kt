package de.danielscholz.fileIndexer.argumentParser

import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.parser.ParamParserBase

class MyPathParam(private val checkIsDir: Boolean = false) : ParamParserBase<MyPath, MyPath?>() {

   override var callback: ((MyPath) -> Unit)? = null
   private var value: MyPath? = null

   override fun numberOfSeparateValueArgsToAccept(): IntRange? {
      return 1..1
   }

   override fun matches(rawValue: String): Boolean {
      return rawValue != ""
   }

   override fun assign(rawValue: String) {
      val path = MyPath.of(rawValue)

      if (checkIsDir && !path.toFile().isDirectory) throw ArgParseException("$path is no directory!", argParser!!)

      value = path
   }

   override fun exec() {
      callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
   }

   override fun convertToStr(value: MyPath?): String? {
      return if (value != null) "'$value'" else null
   }

   override fun printout(): String {
      return "path"
   }
}