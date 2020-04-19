package de.danielscholz.fileIndexer.common

import com.google.common.collect.*
import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.persistence.FileLocation
import de.danielscholz.fileIndexer.persistence.common.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties

fun parseCmd(arg: String): List<String> {
   val result = mutableListOf<String>()
   var lastIdx = -1
   var i = 0
   var inStr = false
   arg.forEach { char ->
      if (char == '"') {
         inStr = !inStr
      } else if (!inStr && char == ' ') {
         result.add(arg.substring(lastIdx + 1, i))
         lastIdx = i
      }
      i++
   }
   result.add(arg.substring(lastIdx + 1, i))
   return result
}

fun testIfCancel(db: Database? = null) {
   if (Global.cancel) {
      db?.commit() // TODO do commit ?
      LoggerFactory.getLogger("Main").debug("DB-committed and exit..")
      throw CancelException()
   }
}

fun registerShutdownCallback(exitCallback: () -> Unit) {
   Runtime.getRuntime().addShutdownHook(object : Thread() {
      override fun run() {
         exitCallback()
      }
   })
}

/**
 * z.B. C:
 */
fun calcFilePathPrefix(dir: File): String {
   return dir.toPath().root?.toString()?.removeSuffix(File.separator) ?: ""
}

/**
 * Fängt immer mit einem / an und endet auch damit.
 * z.B. /test/test2/
 */
fun calcPathWithoutPrefix(dir: File): String {
   return dir.toString().removePrefix(calcFilePathPrefix(dir)).replace("\\", "/").ensureSuffix("/")
}

fun FileLocation.formatOtherData(): String =
      " (Größe: " + (this.fileContent?.fileSize ?: 0).formatAsFileSize() + ", geändert: " + this.modified.convertToLocalZone().toStr() + ")"


// darf nicht inlined werden! Wegen "return" innerhalb von function, dann würde das 2. commit() nicht ausgeführt
fun transaction(logger: Logger, db: Database, function: () -> Unit) {
   try {
      db.commit()
      function() // Kann intern zwischendurch auch commits ausführen!
      db.commit()
   } catch (e: Exception) {
      try {
         db.rollback()
      } catch (ee: Exception) {
         JavaExceptionUtil.addSuppressed(e, ee)
         logger.error("Ein Folgefehler ist aufgetreten (Ursprungsfehler siehe unten)", ee)
      }
      throw e
   }
}

inline fun <T : AutoCloseable, R> T.tryWith(block: (T) -> R): R {
   var closed = false
   try {
      return block(this)
   } catch (e: Exception) {
      closed = true
      try {
         close()
      } catch (closeException: Exception) {
         // eat the closeException as we are already throwing the original cause
         // and we don't want to mask the real exception
         JavaExceptionUtil.addSuppressed(e, closeException)
      }
      throw e
   } finally {
      if (!closed) {
         close()
      }
   }
}

inline fun <T : AutoCloseable, R> Lazy<T>.tryWith(block: (Lazy<T>) -> R): R {
   var closed = false
   try {
      return block(this)
   } catch (e: Exception) {
      closed = true
      try {
         if (isInitialized()) value.close()
      } catch (closeException: Exception) {
         // eat the closeException as we are already throwing the original cause
         // and we don't want to mask the real exception
         JavaExceptionUtil.addSuppressed(e, closeException)
      }
      throw e
   } finally {
      if (!closed) {
         if (isInitialized()) value.close()
      }
   }
}

fun <K, V> mutableSetMultimapOf(): SetMultimap<K, V> = HashMultimap.create<K, V>()
fun <K, V> mutableListMultimapOf(): ListMultimap<K, V> = ArrayListMultimap.create<K, V>()

fun <T> syncronizedMutableListOf(): MutableList<T> = CopyOnWriteArrayList()
fun <K, V> syncronizedMutableMapOf(): MutableMap<K, V> = ConcurrentHashMap()
//fun <T> syncronizedMutableSetOf(): MutableSet<T> = CopyOnWriteArraySet()

fun <K, V> syncronizedMutableSetMultimapOf(): SetMultimap<K, V> = Multimaps.synchronizedSetMultimap(mutableSetMultimapOf())
fun <K, V> syncronizedMutableListMultimapOf(): ListMultimap<K, V> = Multimaps.synchronizedListMultimap(mutableListMultimapOf())

inline fun <T, R : Comparable<R>> Iterable<T>.listMultimapBy(keySelector: (T) -> R?): ListMultimap<R, T> {
   val result = mutableListMultimapOf<R, T>()
   for (it in this) {
      result.put(keySelector(it), it)
   }
   return result
}

operator fun <K, V> ListMultimap<K, V>.set(key: K, value: V) {
   put(key, value)
}

operator fun <K, V> SetMultimap<K, V>.set(key: K, value: V) {
   put(key, value)
}

operator fun AtomicInteger.inc(): AtomicInteger {
   this.incrementAndGet()
   return this
}

operator fun AtomicLong.plusAssign(inc: Long) {
   this.addAndGet(inc)
}

fun AtomicInteger.reset(): AtomicInteger {
   this.set(0)
   return this
}

fun min(a: Int, b: Int): Int {
   return Integer.min(a, b)
}

fun max(a: Int, b: Int): Int {
   return Integer.max(a, b)
}

operator fun <T> Iterator<T>.plus(other: Iterator<T>): Iterator<T> {
   if (!this.hasNext()) return other
   if (!other.hasNext()) return this

   return object : Iterator<T> {
      var first: Boolean = true

      override fun hasNext(): Boolean {
         if (first) {
            if (this.hasNext()) return true
            first = false
         }
         return other.hasNext()
      }

      override fun next(): T {
         if (first) return this.next()
         return other.next()
      }
   }
}

fun <E> listOf(): List<E> = ImmutableList.of()
fun <E> listOf(item: E): List<E> = ImmutableList.of(item)

/**
 * Returns a list containing all elements of the original collection and then all elements of the given [elements] collection.
 */
operator fun <T> List<T>.plus(elements: List<T>): List<T> {
   if (this.isEmpty()) return elements
   if (elements.isEmpty()) return this
   val result = ArrayList<T>(this.size + elements.size)
   result.addAll(this)
   result.addAll(elements)
   return result
}

fun <T> myLazy(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)

fun Long.formatAsFileSize(): String {
   val size = this
   val gb = size * 100 / (1024 * 1024 * 1024)
   if (gb >= 10) {
      return "" + (gb / 100.0) + " GB"
   }
   val mb = size * 10 / (1024 * 1024)
   if (mb >= 10) {
      return "" + (mb / 10.0) + " MB"
   }
   val kb = size * 10 / 1024
   if (kb >= 10) {
      return "" + (kb / 10.0) + " KB"
   }
   return "$size Byte"
}

fun Long.toStr(): String {
   val toString = this.toString()
   if (toString.length > 3) {
      return toString.insert(toString.length - 3, ".")
   }
   return toString
}

// todo Linux?
fun getVolumeSerialNr(dir: File): String? {
   val volumeNr = Files.getFileStore(dir.toPath()).getAttribute("volume:vsn")
   if (volumeNr is Int) {
      return Integer.toHexString(volumeNr).toUpperCase()
   }
   if (volumeNr != null) throw IllegalStateException()
   return null
}

fun getVolumeSerialNr(dir: File, mediumSerialParam: String?): String {
   var mediumSerialDetermined = mediumSerialParam
   if (mediumSerialDetermined.isNullOrEmpty()) {
      mediumSerialDetermined = getVolumeSerialNr(dir)
      if (mediumSerialDetermined.isNullOrEmpty()) {
         throw Exception("MediumSerial of $dir could not be determined. Please specify parameter --mediumSerial with a value.")
      }
   }
   return mediumSerialDetermined
}

fun Config.getCopy(): Config {
   val config = Config()
   val map = mutableMapOf<String, Any?>()
   for (declaredMemberProperty in this::class.declaredMemberProperties) {
      val obj = declaredMemberProperty.getter.call(this)
      map[declaredMemberProperty.name] = obj
   }
   for (declaredMemberProperty in config::class.declaredMemberProperties) {
      if (declaredMemberProperty is KMutableProperty<*>) {
         if (!map.containsKey(declaredMemberProperty.name)) throw java.lang.IllegalStateException()
         val obj = map[declaredMemberProperty.name]
         declaredMemberProperty.setter.call(config, obj)
      } // else throw IllegalStateException("${declaredMemberProperty.name} is not writeable")
   }
   return config
}