package de.danielscholz.fileIndexer.common


//class Result<T> private constructor(private val result: T?, private val exception: Exception? = null) {
//
//   constructor(exception: Exception) : this(null, exception)
//
//   constructor(result: T) : this(result, null)
//
////   fun next(): Result<T> {
////      exception?.addSuppressed(Exception())
////      return this
////   }
//
//   fun get(): T {
//      if (exception != null) {
//         exception.addSuppressed(Exception())
//         throw exception
//      }
//      return result!!
//   }
//}
//
//
//class NoResult(private val exception: Exception? = null) {
//
//   fun next(): NoResult {
//      return this
//   }
//
//   fun handleException() {
//      if (exception != null) {
//         throw exception
//      }
//   }
//}
//
//
//suspend inline fun <T> result(crossinline action: suspend () -> T): Result<T> {
//   try {
//      return Result(action())
//   } catch (e: Exception) {
//      return Result(e)
//   }
//}
//
//suspend inline fun noResult(crossinline action: suspend () -> Unit): NoResult {
//   try {
//      action()
//      return NoResult()
//   } catch (e: Exception) {
//      return NoResult(e)
//   }
//}