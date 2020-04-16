package de.danielscholz.fileIndexer.common

import java.security.MessageDigest
import java.util.*
import kotlin.math.min

class ChunkCreator(private val fileSize: Long) {
   companion object {
      // 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288
      val chunkSizeMap = linkedMapOf<Long, Long>()
      const val minimumChunkSize = 512L

      init {
         var offset = 0L
         var size = minimumChunkSize
         for (i in 0..63) {
            chunkSizeMap[offset] = size
            if (size >= Long.MAX_VALUE / 2) break
            offset += size
            size *= 2
         }
      }
   }

   // Return Values
   var sha1: String? = null
   val sha1ChunksFromBegin = mutableListOf<String>()
   val sha1ChunksFromEnd = mutableListOf<String>()

   // intern
   private val digest = MessageDigest.getInstance("SHA-1")
   private val digestBegin = MessageDigest.getInstance("SHA-1")
   private val digestEnd = MessageDigest.getInstance("SHA-1")
   private var processed = 0L
   private var posFromBegin = 0L
   private var posForEnd = 0L
   private var chunksOffsetBegin = 0L
   private var chunksOffsetEnd = 0L
   private var activeBegin = true
   private var activeEnd = false
   private var startForEnd = -1L
   private val encoder = Base64.getEncoder()
   //	val binaryDistribution = LongArray(256)

   fun init() {
      for ((offset, size) in chunkSizeMap) {
         if (offset + size > fileSize / 2) {
            break
         }
         startForEnd = fileSize - offset - size
         chunksOffsetEnd = offset
      }
   }

   fun update(buffer: ByteArray, count: Int) {
      digest.update(buffer, 0, count)
      processed += count
      if (processed == fileSize) {
         sha1 = encoder.encodeToString(digest.digest())
      }
      updateBegin(buffer, count)
      updateEnd(buffer, count)

      //		for (i in 0..count - 1) {
      //			binaryDistribution[buffer[i].toInt()]++
      //		}
   }

   private fun updateBegin(buffer: ByteArray, count: Int) {
      if (!activeBegin) return
      var bufferOffset = 0
      while (true) {
         val chunkSize = chunkSizeMap[chunksOffsetBegin]!!
         val stillToReadForCurrentChunk = chunksOffsetBegin + chunkSize - posFromBegin

         val toReadFromCurrentBuffer = min(stillToReadForCurrentChunk, count.toLong() - bufferOffset)

         if (toReadFromCurrentBuffer == 0L) break

         digestBegin.update(buffer, bufferOffset, toReadFromCurrentBuffer.toInt())
         posFromBegin += toReadFromCurrentBuffer
         bufferOffset += toReadFromCurrentBuffer.toInt()

         if (stillToReadForCurrentChunk == toReadFromCurrentBuffer) {
            sha1ChunksFromBegin.add(encoder.encodeToString(digestBegin.digest()).getSha1Chunk())
            digestBegin.reset()
            chunksOffsetBegin += chunkSize
            if (posFromBegin + 2 * chunkSize > fileSize / 2) {
               activeBegin = false
               break
            }
         }
      }
   }

   private fun updateEnd(buffer: ByteArray, count: Int) {
      var bufferOffset = 0
      if (!activeEnd) {
         if (startForEnd !in posForEnd..posForEnd + count) {
            posForEnd += count
            return
         }
         bufferOffset = (startForEnd - posForEnd).toInt()
         posForEnd = startForEnd
      }
      activeEnd = true
      while (true) {
         val chunkSize = chunkSizeMap[chunksOffsetEnd]!!
         val stillToReadForCurrentChunk = (fileSize - (chunksOffsetEnd + chunkSize)) + chunkSize - posForEnd

         val toReadFromCurrentBuffer = min(stillToReadForCurrentChunk, count.toLong() - bufferOffset)

         if (toReadFromCurrentBuffer == 0L) break

         digestEnd.update(buffer, bufferOffset, toReadFromCurrentBuffer.toInt())
         posForEnd += toReadFromCurrentBuffer
         bufferOffset += toReadFromCurrentBuffer.toInt()

         if (stillToReadForCurrentChunk == toReadFromCurrentBuffer) {
            sha1ChunksFromEnd.add(0, encoder.encodeToString(digestEnd.digest()).getSha1Chunk())
            digestEnd.reset()
            chunksOffsetEnd -= chunkSize / 2
            if (chunksOffsetEnd < 0L) break
         }
      }
   }

   //	fun getStandardAbweichung() {
   //		var mittelwert = 0.0
   //		for (i in 1..256) {
   //			mittelwert += binaryDistribution[i - 1] * i / fileSize.toDouble()
   //		}
   //		var abweichung = 0.0
   //		for (i in 1..256) {
   //			abweichung += (binaryDistribution[i - 1] * (i - mittelwert ))
   //		}
   //
   //	}
}