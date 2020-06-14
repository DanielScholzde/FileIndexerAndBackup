package de.danielscholz.fileIndexer.argumentParser

import de.danielscholz.fileIndexer.common.MyPath
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.parser.ParamParserBase

class MyPathListParam(private val numberOfFilesToAccept: IntRange = 1..Int.MAX_VALUE,
                      private val checkIsDir: Boolean = false) : ParamParserBase<MutableList<MyPath>, Collection<MyPath>?>() {

   override var callback: ((MutableList<MyPath>) -> Unit)? = null
   private var value: MutableList<MyPath> = mutableListOf()

   override fun numberOfSeparateValueArgsToAccept(): IntRange? {
      return numberOfFilesToAccept
   }

   override fun matches(rawValue: String): Boolean {
      return rawValue != ""
   }

   override fun assign(rawValue: String) {
      val path = MyPath.of(rawValue)

      if (checkIsDir && !path.toFile().isDirectory) throw ArgParseException("$path is no directory!", argParser!!)

      value.add(path)
   }

   override fun exec() {
      callback?.invoke(value) ?: throw ArgParseException("callback must be specified!", argParser!!)
   }

   override fun printout(): String {
      return "path1 path2 .."
   }
}