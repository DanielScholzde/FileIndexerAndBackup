package de.danielscholz.fileIndexer.common

import java.io.ByteArrayInputStream
import java.io.InputStream

interface InputStreamWrapper : AutoCloseable {
   fun read(b: ByteArray): Int
}

class InputStreamWrapperImpl(private val stream: InputStream) : InputStreamWrapper {
   override fun read(b: ByteArray): Int {
      return stream.read(b)
   }

   override fun close() {
      stream.close()
   }
}

class ByteArrayInputStreamWrapperImpl(data: ByteArray) : InputStreamWrapper {

   private val stream = ByteArrayInputStream(data)

   override fun read(b: ByteArray): Int {
      return stream.read(b)
   }

   override fun close() {
      stream.close()
   }
}